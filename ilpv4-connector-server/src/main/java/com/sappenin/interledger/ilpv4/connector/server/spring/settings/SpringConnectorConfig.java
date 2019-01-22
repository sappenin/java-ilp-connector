package com.sappenin.interledger.ilpv4.connector.server.spring.settings;

import com.google.common.eventbus.EventBus;
import com.sappenin.interledger.ilpv4.connector.DefaultILPv4Connector;
import com.sappenin.interledger.ilpv4.connector.ILPv4Connector;
import com.sappenin.interledger.ilpv4.connector.accounts.AccountIdResolver;
import com.sappenin.interledger.ilpv4.connector.accounts.AccountManager;
import com.sappenin.interledger.ilpv4.connector.accounts.AccountSettingsResolver;
import com.sappenin.interledger.ilpv4.connector.accounts.BtpAccountIdResolver;
import com.sappenin.interledger.ilpv4.connector.accounts.DefaultAccountIdResolver;
import com.sappenin.interledger.ilpv4.connector.accounts.DefaultAccountManager;
import com.sappenin.interledger.ilpv4.connector.accounts.DefaultAccountSettingsResolver;
import com.sappenin.interledger.ilpv4.connector.accounts.DefaultPluginManager;
import com.sappenin.interledger.ilpv4.connector.accounts.PluginManager;
import com.sappenin.interledger.ilpv4.connector.fx.DefaultExchangeRateService;
import com.sappenin.interledger.ilpv4.connector.fx.ExchangeRateService;
import com.sappenin.interledger.ilpv4.connector.packetswitch.DefaultILPv4PacketSwitch;
import com.sappenin.interledger.ilpv4.connector.packetswitch.ILPv4PacketSwitch;
import com.sappenin.interledger.ilpv4.connector.plugins.connectivity.PingProtocolPlugin;
import com.sappenin.interledger.ilpv4.connector.plugins.connectivity.PingProtocolPluginFactory;
import com.sappenin.interledger.ilpv4.connector.routing.DefaultRoutingService;
import com.sappenin.interledger.ilpv4.connector.routing.NoOpRoutingService;
import com.sappenin.interledger.ilpv4.connector.routing.RoutingService;
import com.sappenin.interledger.ilpv4.connector.server.spring.settings.btp.SpringBtpConfig;
import com.sappenin.interledger.ilpv4.connector.server.spring.settings.properties.ConnectorSettingsFromPropertyFile;
import com.sappenin.interledger.ilpv4.connector.settings.ConnectorSettings;
import org.interledger.encoding.asn.framework.CodecContext;
import org.interledger.plugin.lpiv2.LoopbackPlugin;
import org.interledger.plugin.lpiv2.btp2.spring.factories.LoopbackPluginFactory;
import org.interledger.plugin.lpiv2.btp2.spring.factories.PluginFactoryProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static com.sappenin.interledger.ilpv4.connector.server.spring.settings.CodecContextConfig.ILP;

/**
 * <p>Primary configuration for the ILPv4 Connector.</p>
 *
 * <p>See the package-info in {@link com.sappenin.interledger.ilpv4.connector.server.spring.settings} for more
 * details.</p>
 */
@Configuration
@EnableConfigurationProperties(ConnectorSettingsFromPropertyFile.class)
@ConditionalOnExpression
@Import(
  {
    CodecContextConfig.class,
    SpringConnectorWebMvc.class,
    //SpringAsyncConfig.class

    // Conditionally loaded if an account connector is running with one or more BTP profiles.
    SpringBtpConfig.class,
  })
public class SpringConnectorConfig {

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ILPv4Connector ilPv4Connector;

  @PostConstruct
  public void onStartup() {

    // Once the Connector has been initialized, we start the routing service in order to initialize any default
    // routes and/or other settings. No need to gate this on any modes, since each implementation of the
    // RoutingService will handle this call properly.

    // TODO This should be replaced by a proper Connector-eventing system that starts and stops various services
    // after the Connector starts. See other parts of this code for some ideas around that system.
    this.ilPv4Connector.getRoutingService().start();
  }

  @Bean
  EventBus eventBus() {
    return new EventBus();
  }

  // This is just a supplier that can be given to beans for later usage after the application has started. This
  // supplier will not resolve to anything until the `ConnectorSettings` bean has been loaded into the
  // application-context, which occurs via the EnableConfigurationProperties annotation on this class.
  @Bean
  Supplier<ConnectorSettings> connectorSettingsSupplier() {

    // The normal `ConnectorSettings` will be the one loaded from the Properties files above (see
    // ConnectorSettingsFromPropertyFile). However, for IT purposes, we want to use the override.

    if (applicationContext.getBean(ConnectorSettings.OVERRIDE_BEAN_NAME) != null) {
      return () -> (ConnectorSettings) applicationContext.getBean(ConnectorSettings.OVERRIDE_BEAN_NAME);
    } else {
      // No override was detected, so return the normal variant that exists because of the EnableConfigurationProperties
      // directive above.
      return () -> applicationContext.getBean(ConnectorSettings.class);
    }
  }

  @Bean
  PluginFactoryProvider pluginFactoryProvider(
    @Qualifier(ILP) CodecContext ilpCodecContext
  ) {
    final PluginFactoryProvider provider = new PluginFactoryProvider();

    // Register known types...Spring will register proper known types based upon config...
    provider.registerPluginFactory(LoopbackPlugin.PLUGIN_TYPE, new LoopbackPluginFactory());
    provider.registerPluginFactory(PingProtocolPlugin.PLUGIN_TYPE, new PingProtocolPluginFactory(ilpCodecContext));

    // TODO: Register any SPI types..
    // See SPI as well as https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/support/SpringFactoriesLoader.html


    return provider;
  }

  @Bean
  PluginManager pluginManager(PluginFactoryProvider pluginFactoryProvider) {
    return new DefaultPluginManager(pluginFactoryProvider);
  }

  @Bean
  AccountManager accountManager(Supplier<ConnectorSettings> connectorSettingsSupplier) {
    return new DefaultAccountManager(connectorSettingsSupplier);
  }

  //  @Bean
  //  RoutingTable<Route> routeRoutingTable() {
  //    return new InMemoryRoutingTable();
  //  }

  @Bean
  ExchangeRateService exchangeRateService() {
    return new DefaultExchangeRateService();
  }

  @Bean
  AccountIdResolver accountIdResolver(BtpAccountIdResolver btpAccountIdResolver) {
    return btpAccountIdResolver;
  }

  @Bean
  BtpAccountIdResolver btpAccountIdResolver() {
    return new DefaultAccountIdResolver();
  }

  @Bean
  AccountSettingsResolver accountSettingsResolver(
    Supplier<ConnectorSettings> connectorSettingsSupplier, AccountIdResolver accountIdResolver,
    AccountManager accountManager
  ) {
    return new DefaultAccountSettingsResolver(connectorSettingsSupplier, accountIdResolver, accountManager);
  }

  @Bean
  @Profile(ConnectorProfile.CONNECTOR_MODE)
  RoutingService connectorModeRoutingService(
    @Qualifier(ILP) CodecContext ilpCodecContext,
    Supplier<ConnectorSettings> connectorSettingsSupplier,
    AccountManager accountManager,
    AccountIdResolver accountIdResolver
  ) {
    return new DefaultRoutingService(ilpCodecContext, connectorSettingsSupplier, accountManager, accountIdResolver);
  }

  @Bean
  @Profile({ConnectorProfile.PLUGIN_MODE})
  RoutingService pluginModePaymentRoutingService() {
    return new NoOpRoutingService();
  }

  @Bean
  ILPv4PacketSwitch ilpPacketSwitch(
    Supplier<ConnectorSettings> connectorSettingsSupplier,
    RoutingService routingService,
    ExchangeRateService exchangeRateService,
    AccountManager accountManager
  ) {
    return new DefaultILPv4PacketSwitch(connectorSettingsSupplier, routingService, exchangeRateService,
      accountManager);
  }

  /**
   * This is necessary to be able to supply references to this connector to dependent services, such as the BTP server
   * handlers.
   */
  @Bean
  ILPv4Connector ilpConnector(
    Supplier<ConnectorSettings> connectorSettingsSupplier,
    AccountIdResolver accountIdResolver,
    AccountSettingsResolver accountSettingsResolver,
    AccountManager accountManager,
    PluginManager pluginManager,
    RoutingService routingService,
    ILPv4PacketSwitch ilpPacketSwitch
  ) {
    // All initialization is performed in DefaultILPv4Connector#init
    return new DefaultILPv4Connector(
      connectorSettingsSupplier,
      accountIdResolver,
      accountSettingsResolver,
      accountManager,
      pluginManager,
      routingService,
      ilpPacketSwitch
    );
  }

  @Bean
  RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  Executor threadPoolTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setThreadNamePrefix("default_task_executor_thread");
    executor.initialize();
    return executor;
  }
}
