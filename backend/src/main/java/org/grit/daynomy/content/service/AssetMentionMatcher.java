package org.grit.daynomy.content.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.grit.daynomy.asset.domain.Asset;
import org.springframework.stereotype.Component;

@Component
class AssetMentionMatcher {

  List<Asset> match(String title, String description, List<Asset> assets) {
    String text = (safe(title) + " " + safe(description)).toLowerCase(Locale.ROOT);
    return assets.stream().filter(asset -> containsName(text, asset.getName())).limit(10).toList();
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
