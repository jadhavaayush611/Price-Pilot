package com.pricepilot.intelligence.discovery;

import com.pricepilot.intelligence.discovery.controller.DiscoveryController;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.dto.SearchSuggestionDTO;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchDiscoveryService discoveryService;

    @Test
    @DisplayName("GET /api/v1/discovery/products returns discovery search results publicly")
    void testDiscoverProductsEndpoint() throws Exception {
        DiscoveryProductDTO product = DiscoveryProductDTO.builder()
                .id(UUID.randomUUID())
                .name("MacBook Pro M3")
                .brand("Apple")
                .category("Laptop")
                .currentBestPrice(BigDecimal.valueOf(1499.00))
                .discoveryBadges(List.of("Best Match", "In Stock"))
                .discoveryReasons(List.of("Exact product name match"))
                .build();

        DiscoverySearchResponseDTO responseDTO = DiscoverySearchResponseDTO.builder()
                .content(List.of(product))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .appliedSort("relevance")
                .executionTimeMs(15)
                .build();

        when(discoveryService.searchAndDiscover(any())).thenReturn(responseDTO);

        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("query", "macbook pro")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("MacBook Pro M3")))
                .andExpect(jsonPath("$.content[0].discoveryBadges[0]", is("Best Match")));
    }

    @Test
    @DisplayName("GET /api/v1/discovery/suggestions returns autocomplete suggestions")
    void testGetSuggestionsEndpoint() throws Exception {
        SearchSuggestionDTO s1 = SearchSuggestionDTO.builder()
                .text("Apple")
                .type("BRAND")
                .build();

        SearchSuggestionDTO s2 = SearchSuggestionDTO.builder()
                .text("AirPods Pro")
                .type("PRODUCT")
                .build();

        when(discoveryService.getSuggestions("a", 6)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/v1/discovery/suggestions")
                        .param("query", "a")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].text", is("Apple")))
                .andExpect(jsonPath("$[1].text", is("AirPods Pro")));
    }

    @Test
    @DisplayName("GET /api/v1/discovery/products with invalid sort returns 400 Bad Request")
    void testInvalidSortReturns400() throws Exception {
        when(discoveryService.searchAndDiscover(any())).thenThrow(new IllegalArgumentException("Invalid sort property: injectionField"));

        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("sort", "injectionField,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Invalid sort property")));
    }
}
