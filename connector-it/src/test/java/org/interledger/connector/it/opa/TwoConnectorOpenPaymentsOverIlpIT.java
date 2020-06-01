package org.interledger.connector.it.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.interledger.connector.it.topologies.AbstractTopology.ALICE_CONNECTOR_ADDRESS;
import static org.interledger.connector.it.topologies.AbstractTopology.ALICE_HTTP_BASE_URL;
import static org.interledger.connector.it.topologies.AbstractTopology.BOB_CONNECTOR_ADDRESS;
import static org.interledger.connector.it.topologies.AbstractTopology.BOB_HTTP_BASE_URL;
import static org.interledger.connector.it.topologies.AbstractTopology.EDDY;
import static org.interledger.connector.it.topologies.AbstractTopology.EDDY_ACCOUNT;
import static org.interledger.connector.it.topologies.AbstractTopology.PAUL;
import static org.interledger.connector.it.topologies.AbstractTopology.PAUL_ACCOUNT;
import static org.interledger.connector.it.topologies.AbstractTopology.PETER;
import static org.interledger.connector.it.topologies.AbstractTopology.PETER_ACCOUNT;

import org.interledger.connector.ILPv4Connector;
import org.interledger.connector.it.AbstractIlpOverHttpIT;
import org.interledger.connector.it.ContainerHelper;
import org.interledger.connector.it.ilpoverhttp.TwoConnectorMixedAssetCodeTestIT;
import org.interledger.connector.it.markers.OpenPayments;
import org.interledger.connector.it.topologies.ilpoverhttp.TwoConnectorPeerIlpOverHttpTopology;
import org.interledger.connector.it.topology.AbstractBaseTopology;
import org.interledger.connector.it.topology.Topology;
import org.interledger.connector.opa.model.IlpPaymentDetails;
import org.interledger.connector.opa.model.Invoice;
import org.interledger.connector.opa.model.PaymentNetwork;
import org.interledger.connector.payments.StreamPayment;
import org.interledger.connector.wallet.OpenPaymentsClient;
import org.interledger.core.InterledgerAddress;
import org.interledger.stream.Denominations;

import com.google.common.primitives.UnsignedLong;
import okhttp3.HttpUrl;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(OpenPayments.class)
public class TwoConnectorOpenPaymentsOverIlpIT extends AbstractIlpOverHttpIT {

  private static final Network network = Network.newNetwork();

  private static final Logger LOGGER = LoggerFactory.getLogger(TwoConnectorMixedAssetCodeTestIT.class);

  private static Topology topology = TwoConnectorPeerIlpOverHttpTopology.init(
    Denominations.XRP_MILLI_DROPS, Denominations.XRP_MILLI_DROPS, UnsignedLong.valueOf(1000000000L)
  );

  private static GenericContainer redis = ContainerHelper.redis(network);
  private static GenericContainer postgres = ContainerHelper.postgres(network);

  private ILPv4Connector aliceConnector;
  private ILPv4Connector bobConnector;

  private OpenPaymentsClient aliceClient;
  private OpenPaymentsClient bobClient;

  @BeforeClass
  public static void startTopology() {
    LOGGER.info("Starting test topology `{}`...", topology.toString());
    redis.start();
    postgres.start();
    topology.start();
    LOGGER.info("Test topology `{}` started!", topology.toString());
  }

  @AfterClass
  public static void stopTopology() {
    LOGGER.info("Stopping test topology `{}`...", topology.toString());
    topology.stop();
    postgres.stop();
    redis.stop();
    LOGGER.info("Test topology `{}` stopped!", topology.toString());
  }

  @Before
  public void setUp() throws IOException, TimeoutException {
    aliceConnector = this.getILPv4NodeFromGraph(getAliceConnectorAddress());
    // Note Bob's Connector's address is purposefully a child of Alice due to IL-DCP
    bobConnector = this.getILPv4NodeFromGraph(getBobConnectorAddress());
    this.resetBalanceTracking();
    aliceClient = OpenPaymentsClient.construct(ALICE_HTTP_BASE_URL);
    bobClient = OpenPaymentsClient.construct(BOB_HTTP_BASE_URL);
  }

  @Test
  public void peterCreatesInvoiceForHimselfOnBob() {
    Invoice createdInvoice = createInvoiceForPeter(bobClient, PETER);
    assertThat(createdInvoice.invoiceUrl())
      .isNotEmpty()
      .get()
      .isEqualTo(HttpUrl.get("http://localhost:8081/accounts/peter/invoices/" + createdInvoice.id().value()));
    assertThat(createdInvoice.accountId())
      .isEqualTo(PETER_ACCOUNT);
  }

  @Test
  public void paulCreatesInvoiceForPeterOnBobViaAlice() {
    Invoice createdInvoice = createInvoiceForPeter(aliceClient, PAUL);
    assertThat(createdInvoice.invoiceUrl())
      .isNotEmpty()
      .get()
      .isEqualTo(HttpUrl.get("http://localhost:8081/accounts/peter/invoices/" + createdInvoice.id().value()));
    assertThat(createdInvoice.accountId())
      .isEqualTo(PAUL_ACCOUNT);
    Invoice invoiceOnBob = bobClient.getInvoice(PETER, createdInvoice.id().value());
    assertThat(invoiceOnBob.accountId())
      .isEqualTo(PETER_ACCOUNT);
  }

  @Test
  public void peterGetsHisOwnInvoiceOnBob() {
    Invoice createdInvoice = createInvoiceForPeter(bobClient, PETER);
    Invoice getInvoice = bobClient.getInvoice(PETER, createdInvoice.id().value());

    assertThat(createdInvoice).isEqualTo(getInvoice);
  }

  @Test
  public void paulSyncsInvoiceForPeterOnAliceFromBob() {
    Invoice petersInvoiceOnBob = createInvoiceForPeter(bobClient, PETER);

    Invoice synced = aliceClient.getOrSyncInvoice(PAUL, petersInvoiceOnBob.invoiceUrl().get().toString());
    assertThat(petersInvoiceOnBob)
      .isEqualToIgnoringGivenFields(synced, "accountId", "createdAt", "updatedAt");
    assertThat(petersInvoiceOnBob.accountId())
      .isEqualTo(PETER_ACCOUNT);
    assertThat(synced.accountId())
      .isEqualTo(PAUL_ACCOUNT);
  }

  @Test
  public void getPeterInvoicePaymentDetailsOnBob() {
    Invoice createdInvoice = createInvoiceForPeter(bobClient, PETER);
    IlpPaymentDetails paymentDetails = bobClient.getIlpInvoicePaymentDetails(createdInvoice.accountId().value(), createdInvoice.id().value());

    assertThat(paymentDetails.destinationAddress().getValue()).startsWith("test.bob.spsp.peter");
  }

  @Test
  public void getPeterInvoicePaymentDetailsViaAliceOnBob() {
    Invoice createdInvoice = createInvoiceForPeter(bobClient, PETER);
    Invoice syncedInvoice = aliceClient.getOrSyncInvoice(PAUL, createdInvoice.invoiceUrl().get().toString());
    IlpPaymentDetails paymentDetails = aliceClient.getIlpInvoicePaymentDetails(syncedInvoice.accountId().value(), syncedInvoice.id().value());

    assertThat(paymentDetails.destinationAddress().getValue()).startsWith("test.bob.spsp.peter");
  }

  @Test
  public void paulPaysInvoiceForPeterViaAliceOnBob() {
    Invoice createdInvoiceOnBob = createInvoiceForPeter(bobClient, PETER);

    Invoice createdInvoiceOnAlice = aliceClient.getOrSyncInvoice(PAUL, createdInvoiceOnBob.invoiceUrl().get().toString());
    StreamPayment payment = aliceClient.payInvoice(
      createdInvoiceOnAlice.accountId().value(),
      createdInvoiceOnAlice.id().value(),
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJwYXVsIiwibmFtZSI6InBhdWwiLCJpYXQiOjE1MTYyMzkwMjJ9.rdYwzQKAG8tFC2aRvG3XsW8BFsHxEFnOwcY-17KAA7g",
      Optional.empty()
    );

    assertThat(payment.amount().abs()).isEqualTo(createdInvoiceOnAlice.amount().bigIntegerValue());
    assertThat(payment.deliveredAmount()).isEqualTo(createdInvoiceOnBob.amount());

    Invoice invoiceOnAliceAfterPayment = bobClient.getInvoice(createdInvoiceOnBob.accountId().value(), createdInvoiceOnBob.id().value());
    assertThat(invoiceOnAliceAfterPayment.isPaid());
  }

  @Test
  public void paulPaysInvoiceForEddyAllOnAlice() throws InterruptedException {
    Invoice invoice = Invoice.builder()
      .accountId(EDDY_ACCOUNT)
      .assetCode(Denominations.XRP_MILLI_DROPS.assetCode())
      .assetScale(Denominations.XRP_MILLI_DROPS.assetScale())
      .amount(UnsignedLong.valueOf(100))
      .subject("$localhost:8080/eddy")
      .build();
    Invoice eddyInvoiceOnAlice = aliceClient.createInvoice(EDDY, invoice);
    Invoice syncedInvoice = aliceClient.getOrSyncInvoice(PAUL, eddyInvoiceOnAlice.invoiceUrl().get().toString());
    assertThat(eddyInvoiceOnAlice)
      .isEqualToIgnoringGivenFields(syncedInvoice, "accountId", "createdAt", "updatedAt");
    assertThat(eddyInvoiceOnAlice.accountId())
      .isEqualTo(EDDY_ACCOUNT);
    assertThat(syncedInvoice.accountId())
      .isEqualTo(PAUL_ACCOUNT);

    StreamPayment payment = aliceClient.payInvoice(
      syncedInvoice.accountId().value(),
      syncedInvoice.id().value(),
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJwYXVsIiwibmFtZSI6InBhdWwiLCJpYXQiOjE1MTYyMzkwMjJ9.rdYwzQKAG8tFC2aRvG3XsW8BFsHxEFnOwcY-17KAA7g",
      Optional.empty()
    );
    assertThat(payment.amount().abs()).isEqualTo(syncedInvoice.amount().bigIntegerValue());
    assertThat(payment.deliveredAmount()).isEqualTo(eddyInvoiceOnAlice.amount());

    // Let the payment system event out and the OP system to update
    Thread.sleep(5000);

    Invoice eddysViewOfTheInvoice = aliceClient.getInvoice(EDDY, eddyInvoiceOnAlice.id().value());
    Invoice paulsViewOfTheInvoice = aliceClient.getInvoice(PAUL, syncedInvoice.id().value());

    assertThat(eddysViewOfTheInvoice)
      .isEqualToIgnoringGivenFields(paulsViewOfTheInvoice, "accountId" ,"createdAt", "updatedAt");
    assertThat(eddysViewOfTheInvoice.received()).isEqualTo(eddysViewOfTheInvoice.amount());
    assertThat(paulsViewOfTheInvoice.received()).isEqualTo(paulsViewOfTheInvoice.amount());
  }

  private Invoice createInvoiceForPeter(OpenPaymentsClient client, String accountId) {
    Invoice invoice = Invoice.builder()
      .accountId(PETER_ACCOUNT)
      .assetCode(Denominations.XRP_MILLI_DROPS.assetCode())
      .assetScale(Denominations.XRP_MILLI_DROPS.assetScale())
      .amount(UnsignedLong.valueOf(100))
      .description("IT payment")
      .subject("$localhost:8081/peter")
      .build();

    return client.createInvoice(accountId, invoice);
  }

  @Override
  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  protected AbstractBaseTopology getTopology() {
    return topology;
  }

  @Override
  protected InterledgerAddress getAliceConnectorAddress() {
    return ALICE_CONNECTOR_ADDRESS;
  }

  @Override
  protected InterledgerAddress getBobConnectorAddress() {
    return BOB_CONNECTOR_ADDRESS;
  }
}