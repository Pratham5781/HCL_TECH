package com.utility.billing.operator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BillingServiceTest {
    private final BillingService billing = new BillingService();
    private final LocalDate issueDate = LocalDate.of(2026, 1, 31);

    @Test
    void calculatesInclusiveSlabEdgesTaxArrearsAndLateFee() {
        var quote = billing.calculate(new BigDecimal("100"), List.of(
                slab("0", "100", "2", "10", "5", "10"),
                slab("100.001", null, "3", "0", "0", "0")),
                new BigDecimal("50"), BigDecimal.ZERO, issueDate);

        assertEquals(new BigDecimal("200.00"), quote.energyCharge());
        assertEquals(new BigDecimal("10.00"), quote.fixedCharge());
        assertEquals(new BigDecimal("10.50"), quote.taxAmount());
        assertEquals(new BigDecimal("50.00"), quote.arrears());
        assertEquals(new BigDecimal("5.00"), quote.lateFee());
        assertEquals(new BigDecimal("275.50"), quote.totalAmount());
        assertEquals(LocalDate.of(2026, 2, 5), quote.dueDate());
    }

    @Test
    void appliesRolloverConsumptionFromThePostResetReading() {
        BigDecimal previous = new BigDecimal("999.8");
        BigDecimal current = new BigDecimal("0.2");
        BigDecimal meterMaximum = new BigDecimal("1000");
        BigDecimal consumed = meterMaximum.subtract(previous).add(current);
        assertEquals(new BigDecimal("0.4"), consumed);
    }

    @Test
    void rejectsConsumptionOutsideConfiguredSlabs() {
        var slabs = List.of(slab("0", "100", "2", "0", "0", "0"));
        assertThrows(ResponseStatusException.class, () -> billing.calculate(new BigDecimal("101"), slabs, BigDecimal.ZERO, BigDecimal.ZERO, issueDate));
    }

    @Test
    void calculatesLpgCylinderChargesAndSubsidy() {
        var quote = billing.calculateLpg(2, new BigDecimal("900"), new BigDecimal("100"), new BigDecimal("40"), issueDate, 5);
        assertEquals(new BigDecimal("1840.00"), quote.totalAmount());
        assertEquals(new BigDecimal("-100.00"), quote.adjustments());
    }

    private BillingService.TariffRate slab(String start, String end, String rate, String fixed, String tax, String fee) {
        return new BillingService.TariffRate(new BigDecimal(start), end == null ? null : new BigDecimal(end), new BigDecimal(rate), new BigDecimal(fixed), new BigDecimal(tax), new BigDecimal(fee), 5);
    }
}
