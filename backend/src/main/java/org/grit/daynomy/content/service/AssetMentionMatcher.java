package org.grit.daynomy.content.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.grit.daynomy.asset.domain.Asset;
import org.springframework.stereotype.Component;

@Component
class AssetMentionMatcher {

  private static final Pattern ENGLISH_COMPANY_SUFFIX =
      Pattern.compile(
          "[,.]?\\s+(?:incorporated|inc|corporation|corp|company|co|limited|ltd|plc)\\.?$",
          Pattern.CASE_INSENSITIVE);
  private static final String KOREAN_POSTPOSITIONS = "에서|에게|으로|부터|까지|은|는|이|가|을|를|의|과|와|도|만|에|로";

  List<Asset> match(String title, String description, List<Asset> assets) {
    String text = (safe(title) + " " + safe(description)).toLowerCase(Locale.ROOT);
    return assets.stream()
        .filter(
            asset ->
                containsName(text, asset.getName())
                    || containsKoreanName(text, asset.getNameKo())
                    || containsName(
                        text, withoutUsCompanySuffix(asset.getName(), asset.getAssetCode()))
                    || containsAssetCode(text, asset.getAssetCode())
                    || containsUsTicker(text, withoutUsSuffix(asset.getAssetCode())))
        .limit(10)
        .toList();
  }

  private boolean containsKoreanName(String text, String name) {
    if (name == null || name.strip().length() < 2) {
      return false;
    }
    String normalizedName = name.strip().toLowerCase(Locale.ROOT);
    return Pattern.compile(
            "(?<![a-z0-9가-힣])"
                + Pattern.quote(normalizedName)
                + "(?=$|[^a-z0-9가-힣]|(?:"
                + KOREAN_POSTPOSITIONS
                + ")++(?=$|[^a-z0-9가-힣]))",
            Pattern.CASE_INSENSITIVE)
        .matcher(text)
        .find();
  }

  private boolean containsUsTicker(String text, String ticker) {
    if (ticker == null || ticker.isBlank()) {
      return false;
    }
    String stripped = ticker.strip();
    return containsAssetCode(text, stripped.length() <= 2 ? "$" + stripped : stripped);
  }

  private String withoutUsCompanySuffix(String name, String assetCode) {
    if (name == null || withoutUsSuffix(assetCode) == null) {
      return null;
    }
    String stripped = name.strip();
    String shortened = ENGLISH_COMPANY_SUFFIX.matcher(stripped).replaceFirst("").strip();
    return shortened.equals(stripped) ? null : shortened;
  }

  private String withoutUsSuffix(String assetCode) {
    if (assetCode == null) {
      return null;
    }
    String stripped = assetCode.strip();
    return stripped.toLowerCase(Locale.ROOT).endsWith(".us")
        ? stripped.substring(0, stripped.length() - 3)
        : null;
  }

  private boolean containsAssetCode(String text, String assetCode) {
    if (assetCode == null || assetCode.strip().length() < 2) {
      return false;
    }
    String normalizedCode = assetCode.strip().toLowerCase(Locale.ROOT);
    return Pattern.compile(
            "(?<![a-z0-9])" + Pattern.quote(normalizedCode) + "(?![a-z0-9])",
            Pattern.CASE_INSENSITIVE)
        .matcher(text)
        .find();
  }

  private boolean containsName(String text, String name) {
    if (name == null || name.strip().length() < 2) {
      return false;
    }
    String normalizedName = name.strip().toLowerCase(Locale.ROOT);
    if (normalizedName.matches("[a-z0-9 .&-]+")) {
      return Pattern.compile(
              "(?<![a-z0-9])" + Pattern.quote(normalizedName) + "(?![a-z0-9])",
              Pattern.CASE_INSENSITIVE)
          .matcher(text)
          .find();
    }
    return text.contains(normalizedName);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
