package cl.uss.ca.service;

import cl.uss.ca.api.dto.IndicatorDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CatalogService {

    public PageResponse<ResearchOutputSummaryDto> emptyOutputs(int page, int size) {
        return PageResponse.of(List.of(), page, size, 0);
    }

    public PageResponse<Object> emptyPage(int page, int size) {
        return PageResponse.of(List.of(), page, size, 0);
    }

    public List<IndicatorDto> layerTwoCatalog() {
        return List.of(
                unavailable(2, "resultado", "R1"),
                unavailable(2, "resultado", "R2"),
                unavailable(2, "resultado", "R3"),
                unavailable(2, "resultado", "R4"),
                unavailable(2, "resultado", "R5"),
                unavailable(2, "proceso", "P1"),
                unavailable(2, "proceso", "P2"),
                unavailable(2, "proceso", "P3"),
                unavailable(2, "formacion", "F1"),
                unavailable(2, "formacion", "F2")
        );
    }

    private static IndicatorDto unavailable(int layer, String group, String code) {
        IndicatorDto dto = new IndicatorDto();
        dto.layer = layer;
        dto.group = group;
        dto.code = code;
        dto.available = false;
        dto.value = null;
        dto.unavailableReason = "NOT_CALCULATED";
        return dto;
    }
}
