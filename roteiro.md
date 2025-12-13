## Roteiro do workshop

### Geração dos slides

1. Criar um servidor http

```sh
bb serve 3000 ws-clojure.smd
```


2. Habilitar live reload
```sh
bb watch ws-clojure.smd
```


3. Conectar no REPL

Jack-in -> Babashka

> Exemplos: sum, say-hello

4. Gerar estatísticas

```clj
(load-file "scripts/generate_statistics.clj")

(generate-statistics/save-survey-stats-slide "ws-clojure.smd")
```
