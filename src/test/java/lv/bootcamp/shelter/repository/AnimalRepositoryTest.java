package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ticket: ANIMAL-3 (see README).
 * Tests status filtering and the database's unique microchip constraint.
 * Follows the persistence pattern in AdopterRepositoryTest.
 */
@DataJpaTest
class AnimalRepositoryTest {

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByStatus_shouldReturnOnlyMatchingAnimals() {
        entityManager.persist(newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE));
        entityManager.persist(newAnimal("Mia", AnimalType.CAT, AnimalStatus.ADOPTED));
        entityManager.flush();

        var available = animalRepository.findByStatus(AnimalStatus.AVAILABLE);

        assertThat(available).hasSize(1);
        assertThat(available.getFirst().getName()).isEqualTo("Rex");
    }

    // The real database must reject duplicate chips even without a service check.
    @Test
    void save_shouldFailWhenMicrochipIdIsDuplicated() {
        Animal first = newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE);
        first.setMicrochipId("CHIP-001");
        entityManager.persistAndFlush(first);
        Animal duplicate = newAnimal("Mia", AnimalType.CAT, AnimalStatus.AVAILABLE);
        duplicate.setMicrochipId("CHIP-001");

        assertThatThrownBy(() -> animalRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Animal newAnimal(String name, AnimalType type, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(status);
        return animal;
    }
}
