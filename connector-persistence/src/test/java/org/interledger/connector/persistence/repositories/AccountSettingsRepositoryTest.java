package org.interledger.connector.persistence.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import okhttp3.HttpUrl;
import org.interledger.connector.accounts.AccountBalanceSettings;
import org.interledger.connector.accounts.AccountId;
import org.interledger.connector.accounts.AccountRateLimitSettings;
import org.interledger.connector.accounts.AccountRelationship;
import org.interledger.connector.accounts.AccountSettings;
import org.interledger.connector.accounts.SettlementEngineAccountId;
import org.interledger.connector.accounts.SettlementEngineDetails;
import org.interledger.connector.link.LinkType;
import org.interledger.connector.persistence.config.ConnectorPersistenceConfig;
import org.interledger.connector.persistence.entities.AccountSettingsEntity;
import org.interledger.connector.persistence.entities.SettlementEngineDetailsEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link AccountSettingsRepository}.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {
  ConnectorPersistenceConfig.class, AccountSettingsRepositoryTest.TestPersistenceConfig.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DataJpaTest
public class AccountSettingsRepositoryTest {

  @Autowired
  private AccountSettingsRepository accountSettingsRepository;

  @Test
  public void whenSaveAndLoadWithAllFieldsPopulated() {
    final Map<String, Object> customSettings = Maps.newHashMap();
    customSettings.put("address", "123 Main Street");
    customSettings.put("zipcode", 12345);

    final AccountSettings accountSettings = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .description("description")
      .assetCode("XRP")
      .assetScale(9)
      .maximumPacketAmount(10L)
      .linkType(LinkType.of("Loopback"))
      .isConnectionInitiator(true)
      .isInternal(true)
      .accountRelationship(AccountRelationship.PEER)
      .rateLimitSettings(AccountRateLimitSettings.builder()
        .maxPacketsPerSecond(10)
        .build())
      .balanceSettings(AccountBalanceSettings.builder()
        .minBalance(0L)
        .settleThreshold(100L)
        .settleTo(10L)
        .build())
      .settlementEngineDetails(SettlementEngineDetails.builder()
        .baseUrl(HttpUrl.parse("https://example.com"))
        .settlementEngineAccountId(SettlementEngineAccountId.of(UUID.randomUUID().toString()))
        .putCustomSettings("foo", "bar")
        .build())
      .ilpAddressSegment("foo")
      .customSettings(customSettings)
      .build();

    final AccountSettingsEntity accountSettingsEntity = new AccountSettingsEntity(accountSettings);
    assertThat(accountSettingsEntity.getId(), is(nullValue()));
    assertThat(accountSettingsEntity.getNaturalId(), is(accountSettings.getAccountId().value()));
    assertAllFieldsEqual(accountSettingsEntity, new AccountSettingsEntity(accountSettings));

    // Equals methods are not the same, so verify this.
    assertThat(accountSettingsEntity, is(not(accountSettings)));

    final AccountSettingsEntity savedAccountSettingsEntity = accountSettingsRepository.save(accountSettingsEntity);
    assertThat(savedAccountSettingsEntity, is(accountSettingsEntity));
    assertThat(savedAccountSettingsEntity.getId() > 0, is(true));
    assertThat(savedAccountSettingsEntity.getNaturalId(), is(accountSettings.getAccountId().value()));
    assertAllFieldsEqual(savedAccountSettingsEntity, new AccountSettingsEntity(accountSettings));

    final AccountSettingsEntity loadedAccountSettingsEntity =
      accountSettingsRepository.findById(savedAccountSettingsEntity.getId()).get();
    assertThat(loadedAccountSettingsEntity.getId() > 0, is(true));
    assertThat(loadedAccountSettingsEntity.getNaturalId(), is(accountSettings.getAccountId().value()));
    assertAllFieldsEqual(loadedAccountSettingsEntity, new AccountSettingsEntity(accountSettings));

    final AccountSettingsEntity loadedAccountSettingsEntity2 =
      accountSettingsRepository.findByNaturalId(accountSettings.getAccountId().value()).get();
    assertThat(loadedAccountSettingsEntity2.getId(), is(loadedAccountSettingsEntity.getId()));
    assertThat(loadedAccountSettingsEntity2.getNaturalId(), is(loadedAccountSettingsEntity.getNaturalId()));
    assertAllFieldsEqual(loadedAccountSettingsEntity2, new AccountSettingsEntity(accountSettings));
  }

  @Test
  public void whenSaveAndLoadWithMinimalFieldsPopulated() {
    final AccountSettings accountSettings = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PEER)
      .build();

    final AccountSettingsEntity accountSettingsEntity = new AccountSettingsEntity(accountSettings);
    assertThat(accountSettingsEntity.getId(), is(nullValue()));
    assertThat(accountSettingsEntity.getNaturalId(), is(accountSettings.getAccountId().value()));
    assertAllFieldsEqual(accountSettingsEntity, new AccountSettingsEntity(accountSettings));

    // Equals methods are not the same, so verify this.
    assertThat(accountSettingsEntity, is(not(accountSettings)));

    final AccountSettingsEntity savedAccountSettingsEntity = accountSettingsRepository.save(accountSettingsEntity);
    assertThat(savedAccountSettingsEntity, is(accountSettingsEntity));
    assertThat(savedAccountSettingsEntity.getId() > 0, is(true));
    assertThat(savedAccountSettingsEntity.getNaturalId(), is(accountSettings.getAccountId().value()));
    assertAllFieldsEqual(savedAccountSettingsEntity, new AccountSettingsEntity(accountSettings));

    final AccountSettingsEntity loadedAccountSettingsEntity =
      accountSettingsRepository.findById(savedAccountSettingsEntity.getId()).get();
    assertThat(loadedAccountSettingsEntity.getId() > 0, is(true));
    assertThat(loadedAccountSettingsEntity.getNaturalId(), is(accountSettings.getAccountId().value()));
    assertAllFieldsEqual(loadedAccountSettingsEntity, new AccountSettingsEntity(accountSettings));

    // Assert actual loaded values...
    assertThat(loadedAccountSettingsEntity.getAccountRelationship(), is(AccountRelationship.PEER));
    assertThat(loadedAccountSettingsEntity.getLinkType(), is(LinkType.of("Loopback")));
    assertThat(loadedAccountSettingsEntity.getAssetCode(), is("XRP"));
    assertThat(loadedAccountSettingsEntity.getAssetScale(), is(9));
    assertThat(loadedAccountSettingsEntity.getCustomSettings().size(), is(0));
    assertThat(loadedAccountSettingsEntity.getMaximumPacketAmount().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getBalanceSettings().getMinBalance().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getBalanceSettings().getSettleThreshold().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getBalanceSettings().getSettleTo(), is(0L));
    assertThat(loadedAccountSettingsEntity.getRateLimitSettings().getMaxPacketsPerSecond().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.settlementEngineDetails().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getIlpAddressSegment().isPresent(), is(false));

    final AccountSettingsEntity loadedAccountSettingsEntity2 =
      accountSettingsRepository.findByNaturalId(accountSettings.getAccountId().value()).get();
    assertThat(loadedAccountSettingsEntity2.getId(), is(loadedAccountSettingsEntity.getId()));
    assertThat(loadedAccountSettingsEntity2.getNaturalId(), is(loadedAccountSettingsEntity.getNaturalId()));
    assertAllFieldsEqual(loadedAccountSettingsEntity2, new AccountSettingsEntity(accountSettings));

    assertThat(loadedAccountSettingsEntity.getAccountRelationship(), is(AccountRelationship.PEER));
    assertThat(loadedAccountSettingsEntity.getLinkType(), is(LinkType.of("Loopback")));
    assertThat(loadedAccountSettingsEntity.getAssetCode(), is("XRP"));
    assertThat(loadedAccountSettingsEntity.getAssetScale(), is(9));
    assertThat(loadedAccountSettingsEntity.getCustomSettings().size(), is(0));
    assertThat(loadedAccountSettingsEntity.getMaximumPacketAmount().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getBalanceSettings().getMinBalance().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getBalanceSettings().getSettleThreshold().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getBalanceSettings().getSettleTo(), is(0L));
    assertThat(loadedAccountSettingsEntity.getRateLimitSettings().getMaxPacketsPerSecond().isPresent(), is(false));
    assertThat(loadedAccountSettingsEntity.getIlpAddressSegment().isPresent(), is(false));
  }

  @Test
  public void findBySettlementEngineAccountId() {
    final SettlementEngineAccountId settlementEngineAccountId =
      SettlementEngineAccountId.of(UUID.randomUUID().toString());

    final AccountSettings accountSettings1 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PEER)
      .settlementEngineDetails(
        SettlementEngineDetails.builder()
          .baseUrl(HttpUrl.parse("https://example.com"))
          .settlementEngineAccountId(settlementEngineAccountId)
          .build()
      )
      .build();
    final AccountSettingsEntity accountSettingsEntity = new AccountSettingsEntity(accountSettings1);
    accountSettingsRepository.save(accountSettingsEntity);

    Optional<AccountSettingsEntity> actual = accountSettingsRepository
      .findBySettlementEngineAccountId(settlementEngineAccountId);
    assertThat(actual.isPresent(), is(true));

    this.assertAllFieldsEqual(actual.get(), accountSettingsEntity);
  }

  @Test
  public void findBySettlementEngineAccountIdWhenNonExistent() {
    final SettlementEngineAccountId settlementEngineAccountId =
      SettlementEngineAccountId.of(UUID.randomUUID().toString());

    assertThat(
      accountSettingsRepository.findBySettlementEngineAccountId(settlementEngineAccountId)
        .isPresent(),
      is(false)
    );
  }

  @Test
  public void findBySettlementEngineAccountIdWhenIdIsNull() {
    final SettlementEngineAccountId settlementEngineAccountId =
      SettlementEngineAccountId.of(UUID.randomUUID().toString());

    final AccountSettings accountSettings = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PEER)
      .build();
    final AccountSettingsEntity accountSettingsEntity = new AccountSettingsEntity(accountSettings);

    // Construct a SettlementEngineDetailsEntity with null values....
    SettlementEngineDetailsEntity nullValueSettlementEngineDetailsEntity = new SettlementEngineDetailsEntity(
      SettlementEngineDetails.builder()
        .baseUrl(HttpUrl.parse("https://example.com"))
        .settlementEngineAccountId(settlementEngineAccountId)
        .build()
    );
    nullValueSettlementEngineDetailsEntity.setBaseUrl(null);
    nullValueSettlementEngineDetailsEntity.setSettlementEngineAccountId(null);
    accountSettingsEntity.setSettlementEngineDetails(nullValueSettlementEngineDetailsEntity);
    accountSettingsRepository.save(accountSettingsEntity);

    assertThat(
      accountSettingsRepository.findBySettlementEngineAccountId(settlementEngineAccountId)
        .isPresent(),
      is(false)
    );
  }

  @Test
  public void whenFindByAccountRelationship() {
    final AccountSettings accountSettings1 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PEER)
      .build();
    final AccountSettingsEntity accountSettingsEntity1 = new AccountSettingsEntity(accountSettings1);
    accountSettingsRepository.save(accountSettingsEntity1);
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PARENT).isPresent(),
      is(false));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.CHILD).isPresent(),
      is(false));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PEER).isPresent(),
      is(true));

    final AccountSettings accountSettings1b = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.CHILD)
      .build();
    final AccountSettingsEntity accountSettingsEntity1b = new AccountSettingsEntity(accountSettings1b);
    accountSettingsRepository.save(accountSettingsEntity1b);
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PARENT).isPresent(),
      is(false));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.CHILD).isPresent(),
      is(true));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PEER).isPresent(),
      is(true));

    final AccountSettings accountSettings2 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PARENT)
      .build();
    final AccountSettingsEntity accountSettingsEntity2 = new AccountSettingsEntity(accountSettings2);
    accountSettingsRepository.save(accountSettingsEntity2);
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PARENT).get(),
      is(accountSettingsEntity2)); // Always finds the first `PARENT`
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.CHILD).isPresent(),
      is(true));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PEER).isPresent(),
      is(true));

    final AccountSettings accountSettings3 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PARENT)
      .build();
    final AccountSettingsEntity accountSettingsEntity3 = new AccountSettingsEntity(accountSettings3);
    accountSettingsRepository.save(accountSettingsEntity3);
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PARENT).get(),
      is(accountSettingsEntity2)); // Always finds the first `PARENT`
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.CHILD).isPresent(),
      is(true));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PEER).isPresent(),
      is(true));

    final AccountSettings accountSettings4 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PARENT)
      .build();
    final AccountSettingsEntity accountSettingsEntity4 = new AccountSettingsEntity(accountSettings4);
    accountSettingsRepository.save(accountSettingsEntity4);
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PARENT).get(),
      is(accountSettingsEntity2)); // Always finds the first `PARENT`
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.CHILD).isPresent(),
      is(true));
    assertThat(accountSettingsRepository.findFirstByAccountRelationship(AccountRelationship.PEER).isPresent(),
      is(true));
  }

  @Test
  public void whenFindAllByAccountRelationship() {
    final AccountSettings accountSettings1 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PEER)
      .build();
    final AccountSettingsEntity accountSettingsEntity1 = new AccountSettingsEntity(accountSettings1);
    accountSettingsRepository.save(accountSettingsEntity1);
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.PARENT).size(), is(0));
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.CHILD).size(), is(0));
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.PEER).size(), is(1));

    final AccountSettings accountSettings2 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PARENT)
      .build();
    final AccountSettingsEntity accountSettingsEntity2 = new AccountSettingsEntity(accountSettings2);
    accountSettingsRepository.save(accountSettingsEntity2);
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.PARENT).size(), is(1));
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.CHILD).size(), is(0));
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.PEER).size(), is(1));

    final AccountSettings accountSettings3 = AccountSettings.builder()
      .accountId(AccountId.of(UUID.randomUUID().toString()))
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.CHILD)
      .build();
    final AccountSettingsEntity accountSettingsEntity3 = new AccountSettingsEntity(accountSettings3);
    accountSettingsRepository.save(accountSettingsEntity3);
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.PARENT).size(), is(1));
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.CHILD).size(), is(1));
    assertThat(accountSettingsRepository.findByAccountRelationshipIs(AccountRelationship.PEER).size(), is(1));
  }

  @Test(expected = DataIntegrityViolationException.class)
  public void whenAccountSettingsAlreadyExists() {
    final AccountId accountId = AccountId.of(UUID.randomUUID().toString());
    final AccountSettings accountSettings1 = AccountSettings.builder()
      .accountId(accountId)
      .assetCode("XRP")
      .assetScale(9)
      .linkType(LinkType.of("Loopback"))
      .accountRelationship(AccountRelationship.PEER)
      .build();
    final AccountSettingsEntity accountSettingsEntity1 = new AccountSettingsEntity(accountSettings1);
    accountSettingsRepository.save(accountSettingsEntity1);

    AccountSettingsEntity loadedEntity = accountSettingsRepository.findByAccountId(accountId).get();
    assertThat(loadedEntity.getAccountId(), is(accountSettings1.getAccountId()));

    final AccountSettingsEntity duplicateEntity = new AccountSettingsEntity(accountSettings1);
    assertThat(duplicateEntity.getAccountId(), is(accountSettings1.getAccountId()));

    try {
      accountSettingsRepository.save(duplicateEntity);
      accountSettingsRepository.findAll(); // Triggers the flush
      fail("Shouldn't be able to save a duplicate AccountSettings!");
    } catch (DataIntegrityViolationException e) {
      assertThat(e.getMessage().startsWith("could not execute statement"), is(true));
      assertThat(accountSettingsRepository.count(), is(1));
      throw e;
    }
  }

  /**
   * Helper method to ensure two entities are equal by manually comparing each field. This is necessary because the
   * {@link AccountSettings#equals(Object)} does not compare the same fields as {@link
   * AccountSettingsEntity#equals(Object)} in order to support Hibernate.
   *
   * @param entity1
   * @param entity2
   *
   * @return {@code true} if the two objects have equivalent fields, {@code false} otherwise.
   */
  private void assertAllFieldsEqual(final AccountSettingsEntity entity1, final AccountSettingsEntity entity2) {
    Objects.requireNonNull(entity1);
    Objects.requireNonNull(entity2);

    assertThat(entity1.getAccountId(), is(entity2.getAccountId()));
    assertThat(entity1.getAccountRelationship(), is(entity2.getAccountRelationship()));
    assertThat(entity1.getDescription(), is(entity2.getDescription()));
    assertThat(entity1.getLinkType(), is(entity2.getLinkType()));
    assertThat(entity1.getAssetCode(), is(entity2.getAssetCode()));
    assertThat(entity1.getAssetScale(), is(entity2.getAssetScale()));
    assertThat(entity1.getIlpAddressSegment(), is(entity2.getIlpAddressSegment()));
    assertThat(entity1.getMaximumPacketAmount(), is(entity2.getMaximumPacketAmount()));

    // BalanceSettings
    assertThat(entity1.getBalanceSettings().getMinBalance(), is(entity2.getBalanceSettings().getMinBalance()));
    assertThat(entity1.getBalanceSettings().getSettleThreshold(),
      is(entity2.getBalanceSettings().getSettleThreshold()));
    assertThat(entity1.getBalanceSettings().getSettleTo(), is(entity2.getBalanceSettings().getSettleTo()));

    // RateLimitSettings
    assertThat(entity1.getRateLimitSettings().getMaxPacketsPerSecond(),
      is(entity2.getRateLimitSettings().getMaxPacketsPerSecond()));

    // SettlementEngineSettings
    if (entity1.settlementEngineDetails().isPresent()) {
      assertThat(entity1.settlementEngineDetails().isPresent(), is(entity2.settlementEngineDetails().isPresent()));
      assertThat(entity1.settlementEngineDetails().get().baseUrl(),
        is(entity2.settlementEngineDetails().get().baseUrl()));
      assertThat(entity1.settlementEngineDetails().get().settlementEngineAccountId(),
        is(entity2.settlementEngineDetails().get().settlementEngineAccountId()));
      assertThat(entity1.getCustomSettings(), is(entity2.getCustomSettings()));

    } else {
      assertThat(entity1.settlementEngineDetails(), is(entity2.settlementEngineDetails()));
    }
    // CustomSettings
    assertThat(entity1.getCustomSettings(), is(entity2.getCustomSettings()));
  }

  @Configuration("application.yml")
  public static class TestPersistenceConfig {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}