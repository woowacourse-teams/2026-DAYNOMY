package org.grit.daynomy.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "industries",
    uniqueConstraints = @UniqueConstraint(name = "uk_industries_code", columnNames = "code"))
public class Industry extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "active", nullable = false)
  private boolean active;

  private Industry(String code, String name, int displayOrder) {
    this.code = code;
    this.name = name;
    this.displayOrder = displayOrder;
    this.active = true;
  }

  public static Industry create(String code, String name, int displayOrder) {
    return new Industry(code, name, displayOrder);
  }

  public void update(String name, int displayOrder, boolean active) {
    this.name = name;
    this.displayOrder = displayOrder;
    this.active = active;
  }
}
