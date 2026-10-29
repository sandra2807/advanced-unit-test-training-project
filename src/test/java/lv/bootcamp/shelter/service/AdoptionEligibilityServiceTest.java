package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService eligibilityService;

    // Stop immediately when the adopter does not exist.
    @Test
    void evaluateAdoption_shouldRejectMissingAdopter() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.empty());

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(false, RejectionReasons.ADOPTER_NOT_FOUND, 0));
        verifyNoInteractions(animalRepository, notificationClient, auditLogger);
    }

    // A valid adopter cannot adopt an animal that does not exist.
    @Test
    void evaluateAdoption_shouldRejectMissingAnimal() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(newAdopter()));
        when(animalRepository.findById(2L)).thenReturn(Optional.empty());

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(false, RejectionReasons.ANIMAL_NOT_FOUND, 0));
        verifyNoInteractions(notificationClient, auditLogger);
    }

    // Neither reserved nor already adopted animals are available.
    @ParameterizedTest
    @EnumSource(value = AnimalStatus.class, names = {"RESERVED", "ADOPTED"})
    void evaluateAdoption_shouldRejectUnavailableAnimal(AnimalStatus status) {
        Animal animal = newAnimal();
        animal.setStatus(status);
        stubRepositories(newAdopter(), animal);

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(false, RejectionReasons.ANIMAL_NOT_AVAILABLE, 0));
        verifyNoInteractions(notificationClient, auditLogger);
    }

    // Age 17 is below the minimum; the rejection must be audited.
    @Test
    void evaluateAdoption_shouldRejectUnderageAdopter() {
        Adopter adopter = newAdopter();
        adopter.setAge(17);
        stubRepositories(adopter, newAnimal());

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(false, RejectionReasons.UNDERAGE, 0));
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.UNDERAGE);
        verifyNoMoreInteractions(auditLogger);
        verifyNoInteractions(notificationClient);
    }

    // Exactly 18 is old enough, and older adults also qualify.
    @ParameterizedTest
    @ValueSource(ints = {18, 30})
    void evaluateAdoption_shouldApproveAdultAdopter(int age) {
        Adopter adopter = newAdopter();
        adopter.setAge(age);
        stubRepositories(adopter, newAnimal());

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(true, "Approved", 0));
        verify(auditLogger).logApproval(1L, 2L, 0);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        verifyNoMoreInteractions(auditLogger, notificationClient);
    }

    // Counts below, at, and above each property's pet limit exercise >=.
    @ParameterizedTest
    @CsvSource({
            "false, 2, true, 0",
            "false, 3, false, 0",
            "false, 4, false, 0",
            "true, 3, true, 9",
            "true, 4, true, 7",
            "true, 5, false, 0",
            "true, 6, false, 0"
    })
    void evaluateAdoption_shouldApplyPetLimit(boolean largeProperty, int petCount,
                                             boolean approved, int expectedScore) {
        Adopter adopter = newAdopter();
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(petCount);
        stubRepositories(adopter, newAnimal());

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        if (approved) {
            assertThat(result).isEqualTo(new AdoptionResult(true, "Approved", expectedScore));
            verify(auditLogger).logApproval(1L, 2L, expectedScore);
            verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        } else {
            assertThat(result).isEqualTo(new AdoptionResult(false, RejectionReasons.PET_LIMIT_REACHED, 0));
            verify(auditLogger).logRejection(1L, 2L, RejectionReason.PET_LIMIT_REACHED);
            verifyNoInteractions(notificationClient);
        }
        verifyNoMoreInteractions(auditLogger, notificationClient);
    }

    // Only birds and rabbits require a permit in the current business rules.
    @ParameterizedTest
    @CsvSource({"DOG, true", "CAT, true", "OTHER, true", "BIRD, false", "RABBIT, false"})
    void evaluateAdoption_shouldCheckPermitForExoticAnimals(AnimalType type, boolean approved) {
        Animal animal = newAnimal();
        animal.setType(type);
        stubRepositories(newAdopter(), animal);

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        if (approved) {
            assertThat(result).isEqualTo(new AdoptionResult(true, "Approved", 0));
            verify(auditLogger).logApproval(1L, 2L, 0);
            verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        } else {
            assertThat(result).isEqualTo(new AdoptionResult(false, RejectionReasons.EXOTIC_PERMIT_REQUIRED, 0));
            verify(auditLogger).logRejection(1L, 2L, RejectionReason.NO_EXOTIC_PERMIT);
            verifyNoInteractions(notificationClient);
        }
        verifyNoMoreInteractions(auditLogger, notificationClient);
    }

    // Having a permit allows either exotic animal type.
    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void evaluateAdoption_shouldApproveExoticAnimalWithPermit(AnimalType type) {
        Adopter adopter = newAdopter();
        adopter.setExoticPermit(true);
        Animal animal = newAnimal();
        animal.setType(type);
        stubRepositories(adopter, animal);

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(true, "Approved", 0));
        verify(auditLogger).logApproval(1L, 2L, 0);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        verifyNoMoreInteractions(auditLogger, notificationClient);
    }

    // A successful evaluation returns and audits the calculated nonzero score.
    @Test
    void evaluateAdoption_shouldReturnPriorityScoreAndNotify() {
        Adopter adopter = newAdopter();
        adopter.setPreviousAdoptions(4);
        adopter.setLargeProperty(true);
        adopter.setCurrentPetCount(1);
        Animal animal = newAnimal();
        animal.setAge(8);
        stubRepositories(adopter, animal);

        AdoptionResult result = eligibilityService.evaluateAdoption(1L, 2L);

        assertThat(result).isEqualTo(new AdoptionResult(true, "Approved", 48));
        verify(auditLogger).logApproval(1L, 2L, 48);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        verifyNoMoreInteractions(auditLogger, notificationClient);
    }

    // Each row isolates a bonus, a penalty, or the minimum score of zero.
    // Exact values 3 and 7 catch the boundary mutations found by PiTest.
    @ParameterizedTest
    @CsvSource(value = {
            "0, false, 1, 0, 0",
            "1, false, 1, 0, 10",
            "3, false, 1, 0, 10",
            "4, false, 1, 0, 15",
            "0, true, 1, 0, 15",
            "0, false, 7, 0, 0",
            "0, false, 8, 0, 20",
            "0, false, null, 0, 0",
            "1, false, 1, 1, 8",
            "1, false, 1, 2, 6",
            "0, false, 1, 1, 0",
            "4, true, 8, 1, 48"
    }, nullValues = "null")
    void calculatePriorityScore_shouldApplyBonusesAndPetPenalty(int previousAdoptions, boolean largeProperty,
                                                               Integer animalAge, int petCount, int expectedScore) {
        Adopter adopter = newAdopter();
        adopter.setPreviousAdoptions(previousAdoptions);
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(petCount);
        Animal animal = newAnimal();
        animal.setAge(animalAge);

        int score = eligibilityService.calculatePriorityScore(adopter, animal);

        assertThat(score).isEqualTo(expectedScore);
        verifyNoInteractions(adopterRepository, animalRepository, auditLogger, notificationClient);
    }

    private Adopter newAdopter() {
        Adopter adopter = new Adopter();
        adopter.setId(1L);
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(30);
        return adopter;
    }

    private Animal newAnimal() {
        Animal animal = new Animal();
        animal.setId(2L);
        animal.setName("Rex");
        animal.setType(AnimalType.DOG);
        animal.setAge(1);
        animal.setStatus(AnimalStatus.AVAILABLE);
        return animal;
    }

    private void stubRepositories(Adopter adopter, Animal animal) {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal));
    }
}
