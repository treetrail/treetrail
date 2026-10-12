# OpenAPI Overlays with RFC 9535

An [OpenAPI Overlay](https://spec.openapis.org/overlay/latest.html) is a list of actions that change an OpenAPI
description: each action selects nodes with a JSONPath query, its `target`, and updates, removes or copies them.
Overlays keep changes to a description you do not own, or that is generated, out of the description itself: what a
code generator needs, internal operations removed before publishing, servers for a test environment.

Since Overlay 1.1, targets are [RFC 9535](https://www.rfc-editor.org/rfc/rfc9535) queries, and tools must
implement RFC 9535 fully. This page shows how to write such targets, a few recipes, how the actions change a
description, and how to use overlays in a build. The module is `openapi-overlay` (Jackson 2; Jackson 3 on request:
[#98](https://github.com/treetrail/treetrail/issues/98)), the Gradle plugin
`io.github.treetrail.openapi-overlay`; the [README](../README.md#openapi-overlays) shows how to set them up. Both are
not yet released.

The examples on this page work on this description, and a test checks every overlay on the page against it:

```yaml
openapi: 3.1.0
info:
  title: Pets
  version: 1.0.0
  description: An API about pets.
servers:
  - url: https://api.example.com
    description: Production
paths:
  /pets:
    get:
      operationId: listPets
      summary: List the pets
      tags: [pets]
      parameters:
        - {name: limit, in: query, schema: {type: integer}}
      responses:
        '200': {description: The pets}
    post:
      operationId: createPet
      summary: Create a pet (deprecated, use PUT)
      responses:
        '201': {description: Created}
  /pets/{id}:
    get:
      operationId: showPet
      x-internal: true
      parameters:
        - {name: id, in: path, required: true}
      responses:
        '200': {description: The pet}
components:
  schemas:
    Pet:
      properties:
        name: {type: string}
        description: {type: string, description: What the owner says about the pet}
```

## Writing targets

**A filter selects members, not the node it follows.** `$.paths.*` selects the path items; `$.paths.*[?...]`
filters the members of each path item, that is, the operations:

| Target | Selects |
| --- | --- |
| `$.paths.*[?@.operationId == 'showPet']` | the operation `showPet` |
| `$.paths.*.*[?@.operationId == 'showPet']` | nothing: it filters the members *of* the operations |
| `$.paths[?@.get && !@.post]` | path items with a GET and no POST: `/pets/{id}` |

**Parentheses are optional.** `[?@.a == 1]` and `[?(@.a == 1)]` are the same query.

**Names with `-` need brackets.** `@.x-internal` is not a query (a name in dot notation has no `-`); write
`@['x-internal']`. The same goes for `$.paths['/pets']`.

**Comparisons are type-strict.** `@['x-rate-limit'] == 100` does not match the string `'100'`; `@.a == null` matches
a member whose value is `null`, not a missing one.

**Functions.** `match(@.operationId, 'list.*')` matches the whole string, `search(@.summary, 'deprecated')` any part
of it; both use [I-Regexp](https://www.rfc-editor.org/rfc/rfc9485), which has no flags, so write `[Dd]eprecated` for
a case-insensitive letter. `length(@.parameters) > 1`, `count(@.responses.*) > 1` and `value(@.tags[0]) == 'pets'`
count and compare.

**Only string values.** `$..description` also selects a schema property named `description`, which is an object;
an update with a string then fails, because the selected nodes must be of one kind. `search()` is false for anything
but a string, and an empty pattern matches every string, so this selects only string descriptions:

```yaml
overlay: 1.1.0
info: {title: Replace descriptions, version: 1.0.0}
actions:
  - target: $..[?search(@.description, '')].description
    update: See the developer portal.
```

**Slices** take a step: `$.servers[::2]` selects every other server, `$.servers[-1:]` the last one.

## Recipes

Remove the operations marked internal:

```yaml
overlay: 1.1.0
info: {title: Remove internal operations, version: 1.0.0}
actions:
  - target: $.paths.*[?@['x-internal'] == true]
    remove: true
```

Add a header to every operation. An update merges into each operation: where it has no `parameters`, they are
added; where it has them, the arrays are concatenated:

```yaml
overlay: 1.1.0
info: {title: Add a request id header, version: 1.0.0}
actions:
  - target: $.paths.*[?@.operationId]
    update:
      parameters:
        - {name: X-Request-Id, in: header, schema: {type: string}}
```

Rename an operation, and give a parameter without a type one:

```yaml
overlay: 1.1.0
info: {title: Prepare for a code generator, version: 1.0.0}
actions:
  - target: $.paths.*[?@.operationId == 'listPets'].operationId
    update: findAllPets
  - target: $.paths.*.*.parameters[?!@.schema && !@.content]
    update: {schema: {type: string}}
```

Point the description at a local server. Removing first and adding then replaces the list; an update alone would
append to it:

```yaml
overlay: 1.1.0
info: {title: Local servers, version: 1.0.0}
actions:
  - target: $.servers
    remove: true
  - target: $
    update:
      servers:
        - {url: 'http://localhost:8080', description: Local}
```

Add the same response to several operations with a reusable action (Overlay 1.2):

```yaml
overlay: 1.2.0
info: {title: Error responses, version: 1.0.0}
components:
  actions:
    notFound:
      description: Adds a 404 response
      fields:
        update:
          '404': {description: Not found}
actions:
  - $ref: '#/components/actions/notFound'
    target: $.paths['/pets/{id}'].*.responses
  - $ref: '#/components/actions/notFound'
    target: $.paths['/pets'].get.responses
```

Rename a path: make sure the new one exists, copy the old one into it, remove the old one:

```yaml
overlay: 1.1.0
info: {title: Rename a path, version: 1.0.0}
actions:
  - target: $.paths
    update: {'/animals': {}}
  - target: $.paths['/animals']
    copy: $.paths['/pets']
  - target: $.paths['/pets']
    remove: true
```

## How actions change a description

The actions run in order, each on the description as the previous ones left it. A target that selects nothing
changes nothing and gives a warning. What an action does with the selected nodes:

| Action | Selected node | Effect |
| --- | --- | --- |
| `remove: true` | any but the root | removed from its object or array |
| `update` (or the node `copy` selects) | object | merged: new members added; members in both: objects merged recursively, arrays concatenated, primitives replaced |
| | array | an array value is concatenated, any other value appended |
| | primitive (string, number, boolean, `null`) | replaced by a primitive |

The selected nodes of an update must all be objects, all arrays or all primitives, and any other combination of
values (an object for a primitive, `null` for an object) is an error. Where the specification leaves a case open,
the module decides: `remove` wins over `update`, and `update` over `copy`; a node selected twice is changed once; a
`copy` query must select exactly one node; removing the root is an error; Overlay 1.0 overlays follow the rules of
1.1. The Javadoc of `Overlay.applyTo` has the details.

`OverlayResult.changes()` lists what each action did where, for example `/actions/0: MERGED
$['paths']['/pets']['get']` for the header recipe above, which helps to check a new overlay.

## In a build

- **Fail when an overlay no longer fits.** A description changes, a target stops matching, and the overlay quietly
  does nothing. `OverlayOptions.withFailOnNoMatch(true)`, `--strict` on the command line and
  `failOnNoMatch = true` in the Gradle plugin make that an error.
- **Invalid overlays fail before they run,** with every problem at once:

  ```
  Overlay: 'info' is required (and 1 more: /actions/0/target: not an RFC 9535 JSONPath query: Query must start with '$' at position 0: …)
  ```

- **Gradle:** the plugin's task writes `build/openapi-overlay/<name>/<file>`; wire it into the generator through
  its output (see the [README](../README.md#openapi-overlays)).
- **Maven:** run `io.github.treetrail.openapi.overlay.OverlayCommand` with `exec-maven-plugin` in
  `generate-sources`, declared before the generator plugin.

## Coming from other tools

Overlays written for tools built on Jayway JsonPath often use its syntax. Most of it is RFC 9535 too, but not all
of it means the same:

| In the overlay | Jayway JsonPath | RFC 9535 |
| --- | --- | --- |
| `$.paths.*[?(@.operationId == 'x')]` | filters the path items themselves: selects nothing | filters the members of the path items: the operation |
| `[?(@.limit == '100')]` with a number 100 | matches | does not match |
| `[?(@.name =~ /pet.*/)]` | regular expression match | not a query; use `match(@.name, 'pet.*')` |
| `$..book.length()` | function at the end of a path | not a query; use `length()` in a filter |

[jayway-vs-rfc9535.md](jayway-vs-rfc9535.md) lists more differences, measured on the Compliance Test Suite.

Overlays for Speakeasy's tool may use its legacy path syntax (`x-speakeasy-jsonpath: legacy`) or its extension
that selects a member's name (`…description~`); neither is RFC 9535. To remove a member, select the member itself:
`$.tags[?@.name == 'a'].description` with `remove: true`. Outside its strict mode, Speakeasy replaces a node with a
value of another kind instead of failing; `OverlayOptions.withLenientMerge(true)` (`--lenient`,
`lenientMerge = true`) does the same.

## YAML

Descriptions and overlays are read as YAML 1.2 would read them where YAML 1.1 surprises: `NO`, `yes`, `on` and
`off` stay strings, so `enum: [NO, SE]` keeps its country codes; written back, they are quoted. Duplicate keys are
an error, numbers keep their digits (`1.10` stays `1.10`), and descriptions up to 256 MiB are read. Comments are not
kept, as the specification allows; the order of members is.
