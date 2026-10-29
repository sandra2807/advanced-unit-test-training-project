package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Both filters apply, and an adoption exactly at the starting time is included.
    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 10, 12, 0);
        AdoptionRecord recentDog = persistAdoption("Rex", AnimalType.DOG, "anna@example.com", since.plusDays(1));
        AdoptionRecord boundaryDog = persistAdoption("Buddy", AnimalType.DOG, "boris@example.com", since);
        persistAdoption("Mia", AnimalType.CAT, "clara@example.com", since.plusDays(1));
        persistAdoption("Max", AnimalType.DOG, "dan@example.com", since.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> records = adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, since);

        assertThat(records).extracting(AdoptionRecord::getId)
                .containsExactlyInAnyOrder(recentDog.getId(), boundaryDog.getId());
        assertThat(records).extracting(AdoptionRecord::getAdopterEmail)
                .containsExactlyInAnyOrder("anna@example.com", "boris@example.com");
        assertThat(records).allSatisfy(record -> {
            assertThat(record.getAnimal().getType()).isEqualTo(AnimalType.DOG);
            assertThat(record.getAdoptedAt()).isAfterOrEqualTo(since);
        });
    }

    private AdoptionRecord persistAdoption(String name, AnimalType type, String email, LocalDateTime adoptedAt) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        entityManager.persist(animal);

        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(email);
        record.setAdoptedAt(adoptedAt);
        return entityManager.persist(record);
    }
}
