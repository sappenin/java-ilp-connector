package com.sappenin.interledger.ilpv4.connector.accounts;

import com.google.common.annotations.VisibleForTesting;
import com.sappenin.interledger.ilpv4.connector.AccountId;
import com.sappenin.interledger.ilpv4.connector.plugins.connectivity.PingProtocolPlugin;
import org.interledger.btp.BtpSession;
import org.interledger.btp.BtpSessionCredentials;
import org.interledger.plugin.lpiv2.LoopbackPlugin;
import org.interledger.plugin.lpiv2.Plugin;
import org.interledger.plugin.lpiv2.PluginId;
import org.interledger.plugin.lpiv2.btp2.spring.AbstractBtpPlugin;

import java.util.Objects;

/**
 * Default implementation of {@link AccountIdResolver} that looks in the connector config to find corresponding
 * AccountId/AccountProviderId definitions. If none is found, it returns a default account settings.
 *
 * TODO: Use Java SPI here so custom resolvers can be added.
 */
public class DefaultAccountIdResolver implements BtpAccountIdResolver, AccountIdResolver {

  @Override
  public AccountId resolveAccountId(final Plugin<?> plugin) {
    Objects.requireNonNull(plugin);

    // The AccountId for a Connected Plugin MUST be the PluginId...but what about a BTP Server Plugin? If it's
    // connected, then
    if (plugin.isConnected()) {
      return plugin.getPluginId()
        .map(PluginId::value)
        .map(AccountId::of)
        .orElseThrow(() -> new RuntimeException("All connected Plugins MUST have a pluginId!"));
    } else {
      // If a Plugin is disconnected, then throw an exception.
      throw new RuntimeException("Disconnected Plugins do not have an associated account!");

//      if (plugin instanceof LoopbackPlugin) {
//        // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
//        final LoopbackPlugin loopbackPlugin = (LoopbackPlugin) plugin;
//        return AccountId.of(loopbackPlugin.getPluginId().get().value());
//      }
//      if (plugin instanceof AbstractBtpPlugin) {
//        // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
//        final AbstractBtpPlugin abstractBtpPlugin = (AbstractBtpPlugin) plugin;
//        return this.resolveAccountId(abstractBtpPlugin.getBtpSessionCredentials());
//      }
//      if (plugin instanceof PingProtocolPlugin) {
//        // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
//        final PingProtocolPlugin pingProtocolPlugin = (PingProtocolPlugin) plugin;
//        return this.resolveAccountId(pingProtocolPlugin.getPluginId().get());
//      } else {
//        throw new RuntimeException("Unsupported Plugin Class: " + plugin.getClass());
//      }
    }
  }

  //  /**
  //   * Determine the {@link AccountId} for the supplied plugin
  //   *
  //   * @param plugin The plugin to introspect to determine the accountId that it represents.
  //   *
  //   * @return The {@link AccountId} for the supplied plugin.
  //   */
  //  @Override
  //  public AccountId resolveAccountProviderId(Plugin<?> plugin) {
  //    Objects.requireNonNull(plugin);
  //
  //    //    if (plugin instanceof LoopbackPlugin) {
  //    //      // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
  //    //      final LoopbackPlugin loopbackPlugin = (LoopbackPlugin) plugin;
  //    //      return AccountProviderId.of(loopbackPlugin.getPluginId().get().value());
  //    //    }
  //    //    if (plugin instanceof AbstractBtpPlugin) {
  //    //      // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
  //    //      final AbstractBtpPlugin abstractBtpPlugin = (AbstractBtpPlugin) plugin;
  //    //      return this.resolveAccountId(abstractBtpPlugin.getBtpSessionCredentials());
  //    //    }
  //    if (plugin instanceof PingProtocolPlugin) {
  //      final PingProtocolPlugin pingProtocolPlugin = (PingProtocolPlugin) plugin;
  //      return AccountId.of(pingProtocolPlugin.getPluginId().get().value());
  //    } else {
  //      throw new RuntimeException("Unsupported Plugin Class: " + plugin.getClass());
  //    }
  //  }

  /**
   * Determine the {@link AccountId} for the supplied plugin.
   *
   * @param btpSession The {@link BtpSession} to introspect to determine the accountId that it represents.
   *
   * @return The {@link AccountId} for the supplied plugin.
   */
  @Override
  public AccountId resolveAccountId(final BtpSession btpSession) {
    Objects.requireNonNull(btpSession);

    return btpSession.getBtpSessionCredentials()
      .map(this::resolveAccountId)
      .orElseThrow(() -> new RuntimeException("No BtpSessionCredentials found!"));
  }

  /**
   * Determine the {@link AccountId} for the supplied plugin.
   *
   * @param btpSessionCredentials The {@link BtpSession} to introspect to determine the accountId that it represents.
   *
   * @return The {@link AccountId} for the supplied plugin.
   */
  @VisibleForTesting
  protected AccountId resolveAccountId(final BtpSessionCredentials btpSessionCredentials) {
    Objects.requireNonNull(btpSessionCredentials);

    return btpSessionCredentials.getAuthUsername()
      .map(AccountId::of)
      .orElseGet(() -> {
        // No AuthUserName, so get the AuthToken and hash it.
        //Route.HMAC(abstractBtpPlugin.getBtpSessionCredentials().getAuthToken());
        throw new RuntimeException("Not yet implemented!");
      });
  }
}
