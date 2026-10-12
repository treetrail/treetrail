# Overlay before a code generator

`codegen.overlay.yaml` prepares `openapi.yaml` for openapi-generator: it removes internal operations, renames an
operationId, adds a 404 response to every GET (reusable action, Overlay 1.2), points the servers at localhost, adds
missing parameter schemas and removes a reserved extension. All targets are RFC 9535; Jayway JsonPath rejects most
of them.

Checked by hand on 2026-10-11 with openapi-generator-cli 7.26.0:

    java -cp ... io.github.treetrail.jsonpath.overlay.OverlayCommand --strict \
        --overlay codegen.overlay.yaml --output build/openapi.yaml openapi.yaml
    java -jar openapi-generator-cli-7.26.0.jar generate -i build/openapi.yaml -g java --library native -o gen

The generator rejects `openapi.yaml` itself (parameters without schema, reserved `x-oai-traits`) and accepts the
result: PetsApi has `findAllPets` and `createPet`, no `showPet`, and the base URI is `http://localhost:8080`.
