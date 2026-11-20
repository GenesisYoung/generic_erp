package com.gsgd.generic_erp.pmc;

import org.springframework.data.jpa.repository.*;

public interface PersonalEvidenceRepository
        extends JpaRepository<PersonalEvidenceEntity, String>, JpaSpecificationExecutor<PersonalEvidenceEntity> {
}
