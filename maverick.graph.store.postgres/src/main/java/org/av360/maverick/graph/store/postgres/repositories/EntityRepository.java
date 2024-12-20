/*
 * Copyright (c) 2024.
 *
 *  Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 *  European Commission - subsequent versions of the EUPL (the "Licence");
 *
 *  You may not use this work except in compliance with the Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  https://joinup.ec.europa.eu/software/page/eupl5
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package org.av360.maverick.graph.store.postgres.repositories;

import org.av360.maverick.graph.store.postgres.dao.EntityDAO;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EntityRepository extends ReactiveCrudRepository<EntityDAO, Long> {

    /*@Query("SELECT * FROM entities e LEFT JOIN attributes a ON e.id = a.entity_id WHERE e.id = :entityId ")
    Flux<EntityDAO> findEntityWithAttributes(Long entityId);
*/
}