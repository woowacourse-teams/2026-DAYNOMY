package org.grit.daynomy.news.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageSourceInfoTest {

  @Test
  @DisplayName("이미지 출처의 null 값은 빈 값과 NONE 유형으로 정규화된다")
  void normalizeNullValues() {
    ImageSourceInfo source = new ImageSourceInfo(null, null, null, null, null, null);

    assertThat(source).isEqualTo(ImageSourceInfo.empty());
  }

  @Test
  @DisplayName("이미지 생성 방식에 맞는 출처 유형을 생성한다")
  void createSourceTypes() {
    ImageSourceInfo manual = new ImageSourceInfo("출처", "https://example.com", "", "", "");
    ImageSourceInfo wikimedia =
        ImageSourceInfo.wikimedia(
            "Wikimedia Commons", "https://example.com/image", "작성자", "CC BY", "");

    assertThat(ImageSourceInfo.empty().type()).isEqualTo(ImageSourceType.NONE);
    assertThat(ImageSourceInfo.aiGenerated().type()).isEqualTo(ImageSourceType.AI_GENERATED);
    assertThat(ImageSourceInfo.manual().type()).isEqualTo(ImageSourceType.MANUAL);
    assertThat(manual.type()).isEqualTo(ImageSourceType.MANUAL);
    assertThat(wikimedia.type()).isEqualTo(ImageSourceType.WIKIMEDIA);
  }
}
