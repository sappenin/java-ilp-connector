package org.interledger.connector.links;

import org.interledger.connector.accounts.AccountSettings;
import org.interledger.connector.caching.AccountSettingsLoadingCache;
import org.interledger.connector.fx.JavaMoneyUtils;
import org.interledger.connector.packetswitch.InterledgerAddressUtils;
import org.interledger.connector.routing.PaymentRouter;
import org.interledger.connector.routing.Route;
import org.interledger.connector.settings.ConnectorSettings;
import org.interledger.core.InterledgerAddress;
import org.interledger.core.InterledgerErrorCode;
import org.interledger.core.InterledgerPreparePacket;
import org.interledger.core.InterledgerProtocolException;
import org.interledger.core.InterledgerRejectPacket;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.MonetaryConversions;

/**
 * A default implementation of {@link NextHopPacketMapper}.
 */
public class DefaultNextHopPacketMapper implements NextHopPacketMapper {

  private static final String DESTINATION_ADDRESS_IS_UNREACHABLE = "Destination address is unreachable";
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final Supplier<ConnectorSettings> connectorSettingsSupplier;
  private final PaymentRouter<Route> externalRoutingService;
  private final InterledgerAddressUtils addressUtils;
  private final JavaMoneyUtils javaMoneyUtils;
  private final AccountSettingsLoadingCache accountSettingsLoadingCache;
  // extracted as a function for testability
  private final Function<CurrencyUnit, CurrencyConversion> currencyConverter;

  public DefaultNextHopPacketMapper(
    final Supplier<ConnectorSettings> connectorSettingsSupplier,
    final PaymentRouter<Route> externalRoutingService,
    final InterledgerAddressUtils addressUtils,
    final JavaMoneyUtils javaMoneyUtils,
    final AccountSettingsLoadingCache accountSettingsLoadingCache
  ) {
    this(connectorSettingsSupplier, externalRoutingService, addressUtils, javaMoneyUtils, accountSettingsLoadingCache,
      (CurrencyUnit unit) -> MonetaryConversions.getConversion(unit));
  }

  @VisibleForTesting
  DefaultNextHopPacketMapper(
    final Supplier<ConnectorSettings> connectorSettingsSupplier,
    final PaymentRouter<Route> externalRoutingService,
    final InterledgerAddressUtils addressUtils,
    final JavaMoneyUtils javaMoneyUtils,
    final AccountSettingsLoadingCache accountSettingsLoadingCache,
    final Function<CurrencyUnit, CurrencyConversion> currencyConverter
  ) {
    this.connectorSettingsSupplier = Objects.requireNonNull(connectorSettingsSupplier);
    this.externalRoutingService = Objects.requireNonNull(externalRoutingService);
    this.addressUtils = Objects.requireNonNull(addressUtils);
    this.javaMoneyUtils = Objects.requireNonNull(javaMoneyUtils);
    this.accountSettingsLoadingCache = Objects.requireNonNull(accountSettingsLoadingCache);
    this.currencyConverter = currencyConverter;
  }

  /**
   * Construct the <tt>next-hop</tt> ILP prepare packet, meaning a new packet with potentially new pricing, destination,
   * and expiry characteristics. This method also includes the proper "next hop" account that the new packet should be
   * forwarded to in order to continue the Interledger protocol.
   * <p>
   * Given a previous ILP prepare packet (i.e., a {@code sourcePacket}), return the next ILP prepare packet in the
   * chain.
   *
   * @param sourceAccountSettings The {@link AccountSettings} of the peer who sent this packet into the Connector. This
   *                              is typically the remote peer-address configured in a link.
   * @param sourcePacket          The {@link InterledgerPreparePacket} that we received from the source address.
   *
   * @return A {@link InterledgerPreparePacket} that can be sent to the next-hop.
   */
  public NextHopInfo getNextHopPacket(
    final AccountSettings sourceAccountSettings, final InterledgerPreparePacket sourcePacket
  ) throws RuntimeException {

    if (logger.isDebugEnabled()) {
      logger.debug(
        "Constructing NextHop InterledgerPreparePacket. sourceAccountSettings={} packet={}",
        sourceAccountSettings, sourcePacket
      );
    }

    final InterledgerAddress destinationAddress = sourcePacket.getDestination();

    final Route nextHopRoute = this.externalRoutingService.findBestNexHop(destinationAddress)
      .orElseThrow(() -> new InterledgerProtocolException(
          InterledgerRejectPacket.builder()
            .triggeredBy(connectorSettingsSupplier.get().operatorAddress())
            .code(InterledgerErrorCode.F02_UNREACHABLE)
            .message(DESTINATION_ADDRESS_IS_UNREACHABLE)
            .build(),
          String.format(
            "No route found from accountId to destination. sourceAccountSettings=%s destinationAddress=%s",
            sourceAccountSettings, destinationAddress.getValue()
          )
        )
      );

    if (logger.isDebugEnabled()) {
      logger.debug("Determined next hop: {}", nextHopRoute);
    }

    if (sourceAccountSettings.accountId().equals(nextHopRoute.nextHopAccountId())) {
      throw new InterledgerProtocolException(
        InterledgerRejectPacket.builder()
          .triggeredBy(connectorSettingsSupplier.get().operatorAddress())
          .code(InterledgerErrorCode.F02_UNREACHABLE)
          .message(DESTINATION_ADDRESS_IS_UNREACHABLE)
          .build(),
        String.format(
          "Refusing to route payments back to sender. sourceAccountSettings=%s destinationAccount=%s",
          sourceAccountSettings, nextHopRoute.nextHopAccountId()
        )
      );
    }

    final AccountSettings destinationAccountSettings =
      this.accountSettingsLoadingCache.safeGetAccountId(nextHopRoute.nextHopAccountId());

    final UnsignedLong nextAmount = this.determineNextAmount(
      sourceAccountSettings, destinationAccountSettings, sourcePacket
    );

    // This warning is added to alert the Connector operator if a particular path is accruing value on the inbound
    // link but mapping to 0 on the outbound link. This typically occurs if the precision on the inbound link is
    // higher than the precision of the outbound link. For example, a packet with 99 units coming into the Connector
    // on an inbound USD link with a scale of 2 (e.g., 99 cents) might map to a USD link with a scale of 0 (i.e., 1
    // unit equals 1 US Dollar). This will translate into a value of 0 on the outbound linke because of the way the
    // Connector implements rounding. If this happens enough, the incoming link will continue to spend money that
    // will show up in the outbound Link. Generally, this will result in a rejection from the outbound link, but
    // just in case we want the Connector operator to be able to detect this condition.
    if (!sourcePacket.getAmount().equals(UnsignedLong.ZERO) && UnsignedLong.ZERO.equals(nextAmount)) {
      logger.warn(
        "While packet-switching, a non-zero source packet amount translated into a zero-value destination amount. "
          + "sourcePacket={} nextHopRoute={}", sourcePacket, nextHopRoute);
    }

    return NextHopInfo.builder()
      .nextHopAccountId(nextHopRoute.nextHopAccountId())
      .nextHopPacket(
        InterledgerPreparePacket.builder()
          .from(sourcePacket)
          .amount(nextAmount)
          .expiresAt(determineDestinationExpiresAt(Clock.systemUTC(),
            sourcePacket.getExpiresAt(), sourcePacket.getDestination()))
          .build())
      .build();
  }

  /**
   * Given a source account, determine the exchange-rate and new amount that should be used in order to forward the
   * packet to the indicated destination account.
   *
   * @param sourceAccountSettings      The {@link AccountSettings} that sourced the prepare packet.
   * @param destinationAccountSettings The {@link AccountSettings} for the destination account.
   * @param sourcePacket               The {@link InterledgerPreparePacket} being routed.
   *
   * @return An {@link UnsignedLong} in the correct units for the destination account.
   */
  @VisibleForTesting
  protected UnsignedLong determineNextAmount(
    final AccountSettings sourceAccountSettings, final AccountSettings destinationAccountSettings,
    final InterledgerPreparePacket sourcePacket
  ) {
    Objects.requireNonNull(sourceAccountSettings);
    Objects.requireNonNull(destinationAccountSettings);
    Objects.requireNonNull(sourcePacket);

    /**
     * Some notes about the performance of the calls & calculations happening below with regard to the JavaMoney API
     *
     * First, lookup of the `CurrencyUnit` and internal lookups of the `ExchangeRateProvider` reuse the same instances,
     * suggesting that we _should_ already be using cached data. The cost of the lookup involved in
     * `Monetary.getCurrency` looks to be around 2000ns (tested in a 1m iteration loop with randomized currencies).
     *
     * Second, while the Java Money API has a type called `FastMoney` which keeps calculations in `long` format and
     * would presumably be faster than doing boxed type conversion, it has two problems:
     * 1) it forces a scale of 5 which can cause overflow issues, particularly when converting to a currency with a
     * high exchange rate (like EUR to JPY)
     * 2) it uses BigDecimal under the covers for initialization, which means we're still bearing some of the cost
     * of boxed type conversion anyhow
     *
     * Third, JavaMoneyUtils is plenty fast at what it's doing. Calls to toMonetaryAmount seem to run in about 55ns
     * and calls to toInterledgerAmount run in about 380ns.
     *
     * Combining all of these still results in less than 1ms of total performance time on a MacBook Pro with a
     * 2.6 GHz Intel Core i7. It's more than likely that this performance will be lower on virtual machines, but
     * we would have to do additional load testing on those VMs to get a better idea of how big of a deviation we see.
     */
    final CurrencyUnit sourceCurrencyUnit = Monetary.getCurrency(sourceAccountSettings.assetCode());
    final int sourceScale = sourceAccountSettings.assetScale();
    final MonetaryAmount sourceAmount =
      javaMoneyUtils.toMonetaryAmount(sourceCurrencyUnit, sourcePacket.getAmount().bigIntegerValue(), sourceScale);

    final CurrencyUnit destinationCurrencyUnit = Monetary.getCurrency(destinationAccountSettings.assetCode());
    final int destinationScale = destinationAccountSettings.assetScale();
    final CurrencyConversion destCurrencyConversion = currencyConverter.apply(destinationCurrencyUnit);

    return UnsignedLong.valueOf(
      javaMoneyUtils.toInterledgerAmount(sourceAmount.with(destCurrencyConversion), destinationScale));
  }

  /**
   * NOTE: This method only exists in order to get FX info into PubSub + BigQuery. See deprecation note below for more
   * details.
   *
   * @param sourceAccountSettings
   * @param destinationAccountSettings
   * @param sourcePacket
   *
   * @return
   *
   * @deprecated This only exists to facilitate getting FX information into BigQuery. However, this method should be
   *   removed once https://github.com/interledger4j/ilpv4-connector/issues/529 is fixed (see that issue for more
   *   details).
   */
  @Deprecated
  public BigDecimal determineExchangeRate(
    final AccountSettings sourceAccountSettings,
    final AccountSettings destinationAccountSettings,
    final InterledgerPreparePacket sourcePacket
  ) {
    Objects.requireNonNull(sourceAccountSettings);
    Objects.requireNonNull(destinationAccountSettings);
    Objects.requireNonNull(sourcePacket);

    final CurrencyUnit sourceCurrencyUnit = Monetary.getCurrency(sourceAccountSettings.assetCode());
    final int sourceScale = sourceAccountSettings.assetScale();
    final MonetaryAmount sourceAmount =
      javaMoneyUtils.toMonetaryAmount(sourceCurrencyUnit, sourcePacket.getAmount().bigIntegerValue(), sourceScale);

    final CurrencyUnit destinationCurrencyUnit = Monetary.getCurrency(destinationAccountSettings.assetCode());
    final CurrencyConversion destCurrencyConversion = currencyConverter.apply(destinationCurrencyUnit);

    return destCurrencyConversion.getExchangeRate(sourceAmount).getFactor().numberValue(BigDecimal.class);
  }

  /**
   * Determine the correct expiration date/time for a packet based upon the supplied inputs. Generally, this method will
   * reduce the supplied {@code sourceExpiry} value by some configurable amount, which is the amount of time the
   * Connector estimates it needs to process the outgoing packet's response from any upstream ILP node.
   *
   * @param clock              A {@link Clock}, useful for testing purposes.
   * @param sourceExpiry       An {@link Instant} representing the expiry found in the incoming prepare packet.
   * @param destinationAddress An {@link InterledgerAddress} so that this function can optionally ignore expiry
   *                           adjustments depending on the destination address.
   *
   * @return An {@link Instant} that represents the new expiry of this packet. This value will be propagated to any
   *   next-hops.
   *
   * @throws InterledgerProtocolException if the computed timeout is before `now` (i.e., the packet is already
   *                                      expired).
   */
  @VisibleForTesting
  protected Instant determineDestinationExpiresAt(
    final Clock clock, final Instant sourceExpiry, final InterledgerAddress destinationAddress
  ) {
    Objects.requireNonNull(sourceExpiry);

    if (!this.addressUtils.isExternalForwardingAllowed(destinationAddress)) {
      // If this packet is not going to be externally-forwarded, then we don't need to wait for a downstream
      // Connector to process it, so we can leave the expiry unchanged.
      return sourceExpiry;
    } else {
      final Instant nowTime = Instant.now(clock);
      if (sourceExpiry.isBefore(nowTime)) {
        throw new InterledgerProtocolException(
          InterledgerRejectPacket.builder()
            .triggeredBy(connectorSettingsSupplier.get().operatorAddress())
            .code(InterledgerErrorCode.R02_INSUFFICIENT_TIMEOUT)
            .message(String
              .format("Source transfer has already expired. sourceExpiry: {%s}, currentTime: {%s}", sourceExpiry,
                nowTime))
            .build()
        );
      }

      ////////////////////
      // We will set the next transfer's expiry based on the source expiry and our minMessageWindowMillis, but cap it at our
      // maxHoldTime.
      ////////////////////
      final int minMessageWindow = connectorSettingsSupplier.get().minMessageWindowMillis();
      final int maxHoldTime = connectorSettingsSupplier.get().maxHoldTimeMillis();

      // The expiry of the packet, reduced by the minMessageWindowMillis, which is "the minimum time the connector wants to
      // budget for getting a message to the accounts its trading on. In milliseconds.  "
      final Instant adjustedSourceExpiryInstant = sourceExpiry.minusMillis(minMessageWindow);
      // The point in time after which this Connector will not wait around for a fulfillment.
      final Instant maxHoldInstant = nowTime.plusMillis(maxHoldTime);
      final Instant destinationExpiryTime = lesser(adjustedSourceExpiryInstant, maxHoldInstant);

      // One final check for a "too soon" expiry...
      if (destinationExpiryTime.minusMillis(minMessageWindow).isBefore(nowTime)) {
        throw new InterledgerProtocolException(
          InterledgerRejectPacket.builder()
            .triggeredBy(connectorSettingsSupplier.get().operatorAddress())
            .code(InterledgerErrorCode.R02_INSUFFICIENT_TIMEOUT)
            .message(String.format(
              "Source transfer expires too soon to complete payment. SourceExpiry: {%s}, " +
                "RequiredSourceExpiry: {%s}, CurrentTime: {%s}",
              sourceExpiry,
              nowTime.plusMillis(minMessageWindow),
              nowTime))
            .build()
        );
      } else {
        return destinationExpiryTime;
      }
    }
  }

  @VisibleForTesting
  protected Instant lesser(final Instant first, final Instant second) {
    if (first.isBefore(second)) {
      return first;
    } else {
      return second;
    }
  }
}
