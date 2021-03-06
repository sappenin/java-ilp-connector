package org.interledger.connector.routing;

import org.immutables.value.Value;
import org.immutables.value.Value.Default;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * <p>An entry in a {@link RoutingTable}, used by Interledger nodes to determine the "next hop" account that a payment
 * should be forwarded to in order to complete a payment.</p>
 *
 * <p> For more details about the structure of this class as it relates to other routes in a routing table, reference
 * {@link RoutingTable}.</p>
 */
public interface SourceRestrictedRoute extends Route {

  String ALLOW_ALL_SOURCES = "(.*?)";

  /**
   * <p>A regular expression that can restrict routing table destinations to a subset of allowed payment-source
   * prefixes. By default, this filter allows all sources.</p>
   *
   * @return A {@link Pattern}
   */
  default Pattern sourcePrefixRestrictionRegex() {
    // Default to allow all sources.
    return Pattern.compile(ALLOW_ALL_SOURCES);
  }

  /**
   * An abstract implementation of {@link SourceRestrictedRoute} for usage by Immutables.
   *
   * @see "https://immutables.github.io"
   */
  @Value.Immutable
  abstract class AbstractSourceRestrictedRoute implements SourceRestrictedRoute {

    @Default
    @Override
    public Pattern sourcePrefixRestrictionRegex() {
      // Default to allow all sources.
      return Pattern.compile(ALLOW_ALL_SOURCES);
    }

    // These are overridden because Pattern does not define an equals method, which default to Object equality, making
    // no pattern ever equal to any other pattern.

    /**
     * This instance is equal to all instances of {@code SourceRestrictedRoute} that have equal attribute values.
     *
     * @return {@code true} if {@code this} is equal to {@code another} instance
     */
    @Override
    public boolean equals(Object another) {
      if (this == another) {
        return true;
      }
      return another instanceof AbstractSourceRestrictedRoute
        && equalTo((AbstractSourceRestrictedRoute) another);
    }

    // TODO: Use rely on parent for seeding the equalTo and hashCode.
    private boolean equalTo(AbstractSourceRestrictedRoute another) {
      return sourcePrefixRestrictionRegex().pattern().equals(another.sourcePrefixRestrictionRegex().pattern())
        && Arrays.equals(auth(), another.auth())
        && routePrefix().equals(another.routePrefix())
        && nextHopAccountId().equals(another.nextHopAccountId())
        && path().equals(another.path())
        && Objects.equals(expiresAt(), another.expiresAt());
    }

    /**
     * Computes a hash code from attributes: {@code sourcePrefixRestrictionRegex}, {@code auth}, {@code targetPrefix},
     * {@code nextHopAccount}, {@code path}, {@code expiresAt}.
     *
     * @return hashCode value
     */
    @Override
    public int hashCode() {
      int h = 5381;
      h += (h << 5) + sourcePrefixRestrictionRegex().pattern().hashCode();
      h += (h << 5) + Arrays.hashCode(auth());
      h += (h << 5) + routePrefix().hashCode();
      h += (h << 5) + nextHopAccountId().hashCode();
      h += (h << 5) + path().hashCode();
      h += (h << 5) + Objects.hashCode(expiresAt());
      return h;
    }

  }
}
