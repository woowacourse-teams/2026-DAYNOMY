package org.grit.daynomy.investmentcalendar.external;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.investmentcalendar.service.InvestmentEventEntry;

public interface InvestmentCalendarProvider {

  List<InvestmentEventEntry> fetch(LocalDate today, int historyYears, int dartLookbackDays);
}
