package lv.bootcamp.shelter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.config.SecurityConfig;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket: ANIMAL-2 (see README).
 * Tests not-found responses, JSON listings, and request validation.
 * Follows the MockMvc pattern in AdopterControllerTest.
 * <p>
 * @Import(SecurityConfig.class) is required: without it, this slice falls
 * back to Spring Boot's default "require auth for everything" security
 * instead of this project's actual (permissive, for these endpoints) rules.
 */
@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnimalService animalService;

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(animalService.findById(99L)).thenThrow(new AnimalNotFoundException(99L));

        mockMvc.perform(get("/api/animals/99"))
                .andExpect(status().isNotFound());
    }

    // Listing animals returns both complete response objects as JSON.
    @Test
    void findAll_shouldReturnAnimalsAsJson() throws Exception {
        List<AnimalResponse> animals = List.of(
                new AnimalResponse(1L, "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", AnimalStatus.AVAILABLE),
                new AnimalResponse(2L, "Mia", AnimalType.CAT, "Siamese", 2, "Quiet", AnimalStatus.RESERVED));
        when(animalService.findAll()).thenReturn(animals);

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(animals), JsonCompareMode.STRICT));

        verify(animalService).findAll();
    }

    // Invalid fields must cause validation failure before the service is called.
    @Test
    void create_shouldReturn400WithoutCallingServiceWhenRequestIsInvalid() throws Exception {
        AnimalCreateRequest request = new AnimalCreateRequest(" ", null, null, -1, null, null);

        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3));

        verifyNoInteractions(animalService);
    }
}
