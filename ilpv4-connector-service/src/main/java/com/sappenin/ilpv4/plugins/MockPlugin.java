package com.sappenin.ilpv4.plugins;

import com.sappenin.ilpv4.model.InterledgerAddress;
import com.sappenin.ilpv4.model.Plugin;
import com.sappenin.ilpv4.model.PluginType;
import com.sappenin.ilpv4.settings.ConnectorSettings;
import org.interledger.core.Fulfillment;
import org.interledger.core.InterledgerFulfillPacket;
import org.interledger.core.InterledgerPreparePacket;
import org.interledger.core.InterledgerProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An implementation of {@link Plugin} that simulates all calls to a faux remote peer.
 */
public class MockPlugin implements Plugin {

  private static final String PREIMAGE = "Roads? Where we're going we don't need roads!";
  private static final String ILP_DATA = "MARTY!";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  // The ILP Address for this plugin.
  private final InterledgerAddress interledgerAddress;
  // The ILP Address of the Connector operating this plugin.
  private final ConnectorSettings connectorSettings;
  private final AtomicBoolean connected;

  /**
   * Required-args Constructor.
   *
   * @param connectorSettings
   * @param interledgerAddress The Interledger Address for this plugin.
   */
  public MockPlugin(final ConnectorSettings connectorSettings, final InterledgerAddress interledgerAddress) {
    this.connectorSettings = Objects.requireNonNull(connectorSettings);
    this.interledgerAddress = Objects.requireNonNull(interledgerAddress);
    this.connected = new AtomicBoolean(false);
  }

  @Override
  public void doConnect() {
    // NO OP
    logger.info("[{}] {} connecting to {}...", this.getPluginType().getPluginDescription(),
      this.getInterledgerAddress(), connectorSettings.getIlpAddress());
    this.connected.compareAndSet(false, true);
    logger
      .info("[{}] {} connected to {}!", this.getPluginType().getPluginDescription(), this.getInterledgerAddress(),
        connectorSettings.getIlpAddress());
  }

  @Override
  public void doDisconnect() {
    // NO OP
    logger
      .info("[{}] for {} disconnecting from {}...", this.getPluginType().getPluginDescription(),
        this.getInterledgerAddress(), connectorSettings.getIlpAddress());
    this.connected.compareAndSet(true, false);
    logger.info("[{}] for {} disconnected from {}!", this.getPluginType().getPluginDescription(),
      this.getInterledgerAddress(), connectorSettings.getIlpAddress());
  }

  /**
   * ILP Prepare packets always fulfill in this Mock plugin.
   */
  @Override
  public CompletableFuture<InterledgerFulfillPacket> sendPacket(InterledgerPreparePacket preparePacket) throws InterledgerProtocolException {
    final InterledgerFulfillPacket ilpFulfillmentPacket = InterledgerFulfillPacket.builder()
      .fulfillment(Fulfillment.of(PREIMAGE.getBytes()))
      .data(ILP_DATA.getBytes())
      .build();

    return CompletableFuture.supplyAsync(() -> ilpFulfillmentPacket);
  }

  @Override
  public void settle(BigInteger amount) {
    // NO OP
    logger.info("[{}] settling {} units via {}!",
      connectorSettings.getIlpAddress(), amount, this.getInterledgerAddress()
    );
  }

  @Override
  public PluginType getPluginType() {
    return PluginType.MOCK;
  }

  @Override
  public InterledgerAddress getInterledgerAddress() {
    return this.interledgerAddress;
  }
}