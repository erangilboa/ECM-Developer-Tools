package com.dctm.workbench.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record TypeDictionary(List<TypeInfo> types) {

    public Optional<TypeInfo> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return types.stream().filter(t -> t.name().equalsIgnoreCase(name)).findFirst();
    }

    public Map<String, List<String>> attributesByType() {
        return types.stream().collect(Collectors.toMap(TypeInfo::name, TypeInfo::attributes, (a, b) -> a));
    }
}
