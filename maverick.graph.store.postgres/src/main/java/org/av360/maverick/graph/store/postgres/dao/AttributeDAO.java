package org.av360.maverick.graph.store.postgres.dao;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "entity_attributes")
public class AttributeDAO extends Base {

    @Id
    private Long id;

    @Column("attribute_name")
    private String attributeName;    //renaming 'property' to 'attributeName'

    @Column("attribute_value")
    private JsonNode value;

    @Column("entity_id")
    private String entityId;
}
