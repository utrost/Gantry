package org.trostheide.watercolorprocessor.dto.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for all plotter operations.
 * Uses Jackson annotations to handle polymorphic JSON serialization based on the "op" field.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "op"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MoveCommand.class, name = "MOVE"),
        @JsonSubTypes.Type(value = DrawCommand.class, name = "DRAW"),
        @JsonSubTypes.Type(value = RefillCommand.class, name = "REFILL")
})
public sealed abstract class Command permits MoveCommand, DrawCommand, RefillCommand {
    public abstract int getId();
}