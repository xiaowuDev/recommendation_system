package xiaowu.example.supplieretl.datasource.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataSourceAiChatApplicationServiceTest {

  @Test
  void shouldNormalizeInsertAliasToAppend() {
    assertThat(DataSourceAiChatApplicationService.normalizeWriteModeAlias("insert"))
        .isEqualTo("APPEND");
  }

  @Test
  void shouldNormalizeMergeAliasToUpsert() {
    assertThat(DataSourceAiChatApplicationService.normalizeWriteModeAlias("MERGE"))
        .isEqualTo("UPSERT");
  }

  @Test
  void shouldNormalizeOverwriteAliasToReplace() {
    assertThat(DataSourceAiChatApplicationService.normalizeWriteModeAlias("overwrite"))
        .isEqualTo("REPLACE");
  }

  @Test
  void shouldKeepSupportedWriteModeUnchanged() {
    assertThat(DataSourceAiChatApplicationService.normalizeWriteModeAlias("APPEND"))
        .isEqualTo("APPEND");
  }

  @Test
  void shouldNormalizeIsNotNullAliasToNotNull() {
    assertThat(DataSourceAiChatApplicationService.normalizeFilterOperatorAlias("IS_NOT_NULL"))
        .isEqualTo("NOT_NULL");
  }

  @Test
  void shouldNormalizeEqualsAliasToEq() {
    assertThat(DataSourceAiChatApplicationService.normalizeFilterOperatorAlias("equals"))
        .isEqualTo("EQ");
  }

  @Test
  void shouldKeepSupportedFilterOperatorUnchanged() {
    assertThat(DataSourceAiChatApplicationService.normalizeFilterOperatorAlias("NOT_NULL"))
        .isEqualTo("NOT_NULL");
  }

  @Test
  void shouldExtractStructuredResultFromAssistantTextWithoutMarker() {
    String assistantText = """
        Reviewed the current connection and recommend reading item_catalog directly.
        {"sourceSummary":"item_catalog with sample rows","suggestionAvailable":true,"warnings":[],"suggestedRequest":{"query":null,"sourceTable":"item_catalog","fieldMappings":[{"sourceField":"item_id","targetField":"item_id","enabled":true}],"filters":[],"transformRules":[],"target":{"targetName":"item_catalog_snapshot","writeMode":"APPEND","primaryKey":"item_id","incrementalField":null},"sortField":"updated_at","sortDirection":"DESC","maxRows":1000}}
        """;

    DataSourceAiChatApplicationService.StructuredResultExtraction extraction =
        DataSourceAiChatApplicationService.extractStructuredResult(assistantText, null);

    assertThat(extraction.assistantMessage())
        .isEqualTo("Reviewed the current connection and recommend reading item_catalog directly.");
    assertThat(extraction.jsonPayload()).contains("\"suggestionAvailable\":true");
    assertThat(extraction.jsonPayload()).contains("\"sourceTable\":\"item_catalog\"");
    assertThat(extraction.warnings()).isEmpty();
  }

  @Test
  void shouldPreferExplicitStructuredPayloadWhenMarkerWasSplitEarlier() {
    DataSourceAiChatApplicationService.StructuredResultExtraction extraction =
        DataSourceAiChatApplicationService.extractStructuredResult(
            "Reviewed the current connection.",
            "{\"sourceSummary\":null,\"suggestionAvailable\":false,\"warnings\":[],\"suggestedRequest\":null}");

    assertThat(extraction.assistantMessage()).isEqualTo("Reviewed the current connection.");
    assertThat(extraction.jsonPayload()).contains("\"suggestionAvailable\":false");
    assertThat(extraction.warnings()).isEmpty();
  }

  @Test
  void shouldFlagMissingStructuredResultWhenNoJsonCanBeFound() {
    DataSourceAiChatApplicationService.StructuredResultExtraction extraction =
        DataSourceAiChatApplicationService.extractStructuredResult(
            "I already reviewed the current connection. You can continue refining the request.",
            null);

    assertThat(extraction.assistantMessage())
        .isEqualTo("I already reviewed the current connection. You can continue refining the request.");
    assertThat(extraction.jsonPayload()).isNull();
    assertThat(extraction.warnings())
        .containsExactly(DataSourceAiChatApplicationService.STRUCTURED_RESULT_MISSING_WARNING);
  }
}
