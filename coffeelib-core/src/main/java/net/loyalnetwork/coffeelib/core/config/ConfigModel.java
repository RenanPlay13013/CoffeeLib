package net.loyalnetwork.coffeelib.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
final class ConfigModel {

    private final String fileName;
    private final List<FieldMetadata> fields;
}
