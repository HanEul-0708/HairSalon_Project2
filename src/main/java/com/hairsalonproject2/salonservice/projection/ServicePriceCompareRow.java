package com.hairsalonproject2.salonservice.projection;

import java.math.BigDecimal;

public interface ServicePriceCompareRow {
    Integer getServiceId();

    String getServiceName();

    Integer getPrice();

    Integer getDuration();

    Integer getSalonId();

    String getSalonName();

    String getAddress();

    BigDecimal getAverageRating();
}