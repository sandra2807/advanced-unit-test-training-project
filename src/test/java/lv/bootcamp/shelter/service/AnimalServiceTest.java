package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ticket: ANIMAL-1 (see README).
 * Tests creation, adoption, reservations, and duplicate microchip rejection.
 * Follows the Mockito pattern in AdopterServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AnimalService animalService;

    @Captor
    private ArgumentCaptor<List<Long>> animalIdsCaptor;

    @Test
    void create_shouldSaveAnimalWithAvailableStatus() {
        Animal saved = new Animal();
        saved.setId(1L);
        saved.setName("Rex");
        saved.setType(AnimalType.DOG);
        saved.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.save(any(Animal.class))).thenReturn(saved);

        AnimalResponse response = animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Friendly", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Rex");
        assertThat(response.status()).isEqualTo(AnimalStatus.AVAILABLE);
    }

    @Test
    void findById_shouldThrowWhenAnimalNotFound() {
        when(animalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalService.findById(99L))
                .isInstanceOf(AnimalNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void adopt_shouldThrowWhenAnimalAlreadyAdopted() {
        Animal adopted = new Animal();
        adopted.setId(1L);
        adopted.setStatus(AnimalStatus.ADOPTED);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(adopted));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    // Adoption saves the new status and sends the correct notification details.
    @Test
    void adopt_shouldSaveAdoptedAnimalAndNotifyAdopter() {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setName("Rex");
        animal.setStatus(AnimalStatus.AVAILABLE);
        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));
        when(animalRepository.save(animal)).thenReturn(animal);

        AnimalResponse response = animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com"));

        ArgumentCaptor<Animal> savedAnimal = ArgumentCaptor.forClass(Animal.class);
        verify(animalRepository).save(savedAnimal.capture());
        assertThat(savedAnimal.getValue().getStatus()).isEqualTo(AnimalStatus.ADOPTED);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Rex");
        assertThat(response.status()).isEqualTo(AnimalStatus.ADOPTED);
        verify(notificationClient).sendAdoptionNotification(1L, "Rex", "anna@example.com");
    }

    // The notification must contain every reserved ID exactly once, in any order.
    @Test
    void reserveMultiple_shouldNotifyWithExactlyTheReservedIds() {
        Animal rex = new Animal();
        rex.setId(1L);
        rex.setStatus(AnimalStatus.AVAILABLE);
        Animal mia = new Animal();
        mia.setId(2L);
        mia.setStatus(AnimalStatus.AVAILABLE);
        List<Long> requestedIds = List.of(1L, 2L);
        when(animalRepository.findAllById(requestedIds)).thenReturn(List.of(mia, rex));
        when(animalRepository.save(rex)).thenReturn(rex);
        when(animalRepository.save(mia)).thenReturn(mia);

        List<AnimalResponse> responses = animalService.reserveMultiple(requestedIds);

        verify(notificationClient).sendBulkStatusNotification(animalIdsCaptor.capture(), eq("RESERVED"));
        assertThat(animalIdsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(responses).extracting(AnimalResponse::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(responses).extracting(AnimalResponse::status).containsOnly(AnimalStatus.RESERVED);
        assertThat(rex.getStatus()).isEqualTo(AnimalStatus.RESERVED);
        assertThat(mia.getStatus()).isEqualTo(AnimalStatus.RESERVED);
        verify(animalRepository).save(rex);
        verify(animalRepository).save(mia);
    }

    // A known microchip is rejected before any animal is saved.
    @Test
    void create_shouldRejectDuplicateMicrochipWithoutSaving() {
        when(animalRepository.existsByMicrochipId("CHIP-001")).thenReturn(true);

        assertThatThrownBy(() -> animalService.create(new AnimalCreateRequest(
                "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", "CHIP-001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Animal with microchip CHIP-001 is already registered");

        verify(animalRepository).existsByMicrochipId("CHIP-001");
        verify(animalRepository, never()).save(any(Animal.class));
    }
}
