# Arquitetura da CoffeeLib — DDD e Object Calisthenics

A CoffeeLib é uma **biblioteca de integração multiplataforma**, não uma aplicação com um domínio de negócio único. Usamos Domain-Driven Design (DDD) para definir limites, contratos e invariantes; Object Calisthenics orienta refatorações, sem impor alterações incompatíveis na API pública.

## Limites entre módulos

| Módulo | Responsabilidade | Não deve conter |
| --- | --- | --- |
| `coffeelib-api` | Contratos públicos estáveis | Classes do Minecraft ou detalhes de persistência |
| `coffeelib-core` | Regras comuns de comandos, permissões, economia, visibilidade, scheduler, mensagens e DI | Dependências diretas de Bukkit, Forge ou NeoForge |
| `coffeelib-database` | Persistência, conexões e transações | Execução no tick ou respostas ao jogador |
| `coffeelib-platform-*` | Adaptadores e ciclo de vida de cada plataforma | Novas regras de domínio divergentes sem necessidade |

## Componentes internos refatorados

- **Comandos:** `CommandScanner` descobre handlers; `CommandArguments` valida os argumentos; `CommandSuggestions` resolve os provedores de sugestões; `CommandPaths` normaliza caminhos e aliases.
- **Injeção de dependências:** `DefaultServiceContainer` mantém a fachada pública; `ProviderCatalog` registra providers; `InjectionPlan` prepara as injeções antes de alterar o handler.
- **Agendamento:** `TickScheduler` coordena threads e encerramento; `TickAgenda` cuida de prazos, períodos e cancelamento.
- **Vanish:** `SharedPlayers` enumera jogadores; `VisibilityPolicies` controla inscrições por dono e aplica falha segura (*fail closed*).
- **Economia:** `RegistryCurrency` conversa com o provider; `MoneyAmountPolicy` valida valores. Transferências atômicas exigem capacidade real do provider.
- **Mensagens:** `DefaultMessages` entrega mensagens; `PlaceholderBindings` valida os pares; `MessageTemplate` interpola uma vez, sem processamento recursivo.
- **Permissões:** `SharedPermissionChanges` entrega notificações; `PermissionChangeBuffer` controla a fila deduplicada entre threads e ciclos de vida.

## Regras de desenvolvimento

1. Preferir métodos pequenos, nomes explícitos, retornos antecipados e estado encapsulado.
2. Colocar cada invariante no componente responsável; não replicar regras entre plataformas.
3. Criar *Value Objects* quando representam regras reais, não apenas para embrulhar cada `String` ou `int`.
4. Manter reflection, I/O, banco, classloaders e APIs Minecraft fora das regras de domínio.
5. Não aplicar rigidamente “um nível de indentação”, “um ponto por linha” ou “sem else” quando isso prejudicar clareza e correção.
6. Preservar contratos públicos, atomicidade de economia, falha segura em vanish e semântica de reload.
7. Testar regras do core e executar build/testes nas versões reais dos loaders antes de liberar para produção.

## Pontos ainda abertos

- Componentes grandes (`ManagedConfigService`, `DefaultConfigManager`, `PooledDatabase` e inicializações das plataformas) exigem refatoração individual com testes; tamanho, isoladamente, não prova defeito.
- Forge e NeoForge possuem trechos semelhantes, mas versões/mappings distintos. A unificação precisa de testes de integração, não apenas comparação textual.
- Registries estáticos globais precisam de revisão para reload, servidores integrados e servidores híbridos.
- A validação local dos componentes **não substitui** o build oficial Gradle nem a homologação em Paper, Forge e NeoForge.
