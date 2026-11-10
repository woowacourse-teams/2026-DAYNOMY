package org.grit.daynomy.external;

import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.ErrorCode;

/** A transient upstream failure without retaining request URLs, credentials, or response bodies. */
public class RetryableExternalRequestException extends BusinessException {
  public RetryableExternalRequestException(ErrorCode errorCode) {
    super(errorCode);
  }
}
