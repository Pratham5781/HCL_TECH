package com.utility.billing.operator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class BillingService {
    private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);

    public BillQuote calculate(BigDecimal units,List<TariffRate> slabs,BigDecimal arrears,BigDecimal adjustments,LocalDate issueDate){
        if(units.signum()<0)throw invalid("Consumption cannot be negative");
        if(slabs.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No effective tariff is configured for this service and connection type");
        BigDecimal energy=BigDecimal.ZERO;BigDecimal expectedStart=BigDecimal.ZERO;TariffRate first=slabs.get(0);boolean covered=units.signum()==0;
        for(TariffRate slab:slabs){
            if(slab.rangeStart().compareTo(expectedStart)!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Tariff slabs must be contiguous and start at zero");
            BigDecimal lower=slab.rangeStart();
            if(units.compareTo(lower)<0){covered=true;break;}
            BigDecimal upper=slab.rangeEnd()==null?units:slab.rangeEnd().min(units);
            if(upper.compareTo(lower)>=0){
                BigDecimal quantity=upper.subtract(lower);
                if(lower.signum()>0&&units.compareTo(lower)>=0)quantity=quantity.add(new BigDecimal("0.001"));
                energy=energy.add(quantity.multiply(slab.ratePerUnit()));
            }
            if(slab.rangeEnd()==null){covered=true;break;}
            if(units.compareTo(slab.rangeEnd())<=0){covered=true;break;}
            expectedStart=slab.rangeEnd().add(new BigDecimal("0.001"));
        }
        if(!covered)throw new ResponseStatusException(HttpStatus.CONFLICT,"Tariff slabs do not cover this consumption amount");
        energy=money(energy);BigDecimal fixed=money(first.fixedCharge());
        BigDecimal tax=money(energy.add(fixed).multiply(first.taxPercent()).divide(HUNDRED,8,RoundingMode.HALF_UP));
        BigDecimal arrearAmount=money(arrears.max(BigDecimal.ZERO));
        BigDecimal late=money(arrearAmount.multiply(first.lateFeePercent()).divide(HUNDRED,8,RoundingMode.HALF_UP));
        BigDecimal adjustment=money(adjustments);
        BigDecimal total=money(energy.add(fixed).add(tax).add(arrearAmount).add(late).add(adjustment));
        if(total.signum()<0)throw invalid("Bill total cannot be negative");
        return new BillQuote(energy,fixed,tax,arrearAmount,late,adjustment,total,issueDate.plusDays(first.dueDays()));
    }

    public BillQuote calculateLpg(int cylinders,BigDecimal unitPrice,BigDecimal subsidy,BigDecimal deliveryCharge,LocalDate issueDate,int dueDays){
        if(cylinders<1||cylinders>20||unitPrice.signum()<0||subsidy.signum()<0||deliveryCharge.signum()<0||dueDays<1)throw invalid("LPG booking values are outside their allowed ranges");
        BigDecimal energy=money(unitPrice.multiply(BigDecimal.valueOf(cylinders)));
        BigDecimal adjustment=money(subsidy.negate());BigDecimal fixed=money(deliveryCharge);
        BigDecimal total=money(energy.add(fixed).add(adjustment));
        if(total.signum()<0)throw invalid("Subsidy cannot exceed the cylinder and delivery charges");
        return new BillQuote(energy,fixed,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,adjustment,total,issueDate.plusDays(dueDays));
    }

    private BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}

    public record TariffRate(BigDecimal rangeStart,BigDecimal rangeEnd,BigDecimal ratePerUnit,BigDecimal fixedCharge,BigDecimal taxPercent,BigDecimal lateFeePercent,int dueDays){}
    public record BillQuote(BigDecimal energyCharge,BigDecimal fixedCharge,BigDecimal taxAmount,BigDecimal arrears,BigDecimal lateFee,BigDecimal adjustments,BigDecimal totalAmount,LocalDate dueDate){}
}

