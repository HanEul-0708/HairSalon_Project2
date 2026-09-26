package com.hairsalonproject2.designer.dto.response;

import org.springframework.data.domain.Page;

public record DesignerCatalogPage(Page<DesignerSummaryResponse> rating,
                                  Page<DesignerSummaryResponse> likes,
                                  Page<DesignerSummaryResponse> newest) {
}
