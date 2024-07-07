


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        List<GameLog> logs = generateSampleLogs();

        System.out.println("1. 각 대륙별 최대 동시 접속자 수:");
        findMaxConcurrentUsers(logs);

        System.out.println("\n2. 가장 열정적인 상위 10명의 플레이어:");
        findTopPlayers(logs);

        System.out.println("\n3. 아이템 판매 추이 (3시간 단위):");
        analyzeItemSales(logs);

        System.out.println("\n4. 가장 어려운 5개의 퀘스트:");
        findDifficultQuests(logs);

        System.out.println("\n6. 각 대륙별 플레이어 충성도 순위:");
        calculatePlayerLoyalty(logs);
    }

    private static void findMaxConcurrentUsers(List<GameLog> logs) {
        Map<String, Map.Entry<Integer, Long>> maxConcurrent = logs.stream()
                .collect(Collectors.groupingBy(GameLog::serverId,
                        Collectors.collectingAndThen(Collectors.toList(), serverLogs -> {
                            Map<Long, Integer> concurrentUsers = new TreeMap<>();
                            for (GameLog log : serverLogs) {
                                concurrentUsers.merge(log.timestamp(), switch(log.eventType()) {
                                    case "LOGIN" -> 1;
                                    case "LOGOUT" -> -1;
                                    default -> 0;
                                }, Integer::sum);
                            }
                            int current = 0, max = 0;
                            long maxTime = 0;
                            for (var entry : concurrentUsers.entrySet()) {
                                current += entry.getValue();
                                if (current > max) {
                                    max = current;
                                    maxTime = entry.getKey();
                                }
                            }
                            return Map.entry(max, maxTime);
                        })));

        maxConcurrent.forEach((serverId, entry) ->
                System.out.printf("%s: %d명 (%d)%n", serverId, entry.getKey(), entry.getValue()));
    }

    private static void findTopPlayers(List<GameLog> logs) {
        record PlayerPlayTime(String playerId, int totalPlayTime) {}

        List<PlayerPlayTime> topPlayers = logs.stream()
                .filter(log -> log.eventType().equals("LOGOUT"))
                .collect(Collectors.groupingBy(GameLog::playerId,
                        Collectors.summingInt(GameLog::value)))
                .entrySet().stream()
                .map(entry -> new PlayerPlayTime(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(PlayerPlayTime::totalPlayTime).reversed())
                .limit(10)
                .toList();

        topPlayers.forEach(player ->
                System.out.printf("%s - 총 플레이 시간: %d초%n", player.playerId(), player.totalPlayTime()));
    }

    private static void analyzeItemSales(List<GameLog> logs) {
        record SalesPeriod(long startTime, int totalSales) {}

        List<SalesPeriod> salesByPeriod = logs.stream()
                .filter(log -> log.eventType().equals("ITEM_PURCHASE"))
                .collect(Collectors.groupingBy(
                        log -> log.timestamp() / (3 * 3600) * (3 * 3600),
                        Collectors.summingInt(GameLog::value)
                ))
                .entrySet().stream()
                .map(entry -> new SalesPeriod(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(SalesPeriod::totalSales).reversed())
                .limit(3)
                .toList();

        salesByPeriod.forEach(period -> {
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(period.startTime()), ZoneId.systemDefault());
            System.out.printf("%s: 총 판매액 %d 골드%n", dateTime, period.totalSales());
        });
    }

    private static void findDifficultQuests(List<GameLog> logs) {
        record QuestDifficulty(int questId, long avgCompletionTime) {}

        List<QuestDifficulty> difficultQuests = logs.stream()
                .filter(log -> log.eventType().equals("QUEST_START") || log.eventType().equals("QUEST_COMPLETE"))
                .collect(Collectors.groupingBy(GameLog::value,
                        Collectors.collectingAndThen(Collectors.toList(), questLogs -> {
                            Map<String, Long> startTimes = new HashMap<>();
                            long totalTime = 0;
                            int completions = 0;
                            for (GameLog log : questLogs) {
                                if (log.eventType().equals("QUEST_START")) {
                                    startTimes.put(log.playerId(), log.timestamp());
                                } else {
                                    Long startTime = startTimes.remove(log.playerId());
                                    if (startTime != null) {
                                        totalTime += log.timestamp() - startTime;
                                        completions++;
                                    }
                                }
                            }
                            return completions > 0 ? totalTime / completions : 0;
                        })))
                .entrySet().stream()
                .map(entry -> new QuestDifficulty(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(QuestDifficulty::avgCompletionTime).reversed())
                .limit(5)
                .toList();

        difficultQuests.forEach(quest ->
                System.out.printf("퀘스트 %d: 평균 소요 시간 %d초%n", quest.questId(), quest.avgCompletionTime()));
    }

    private static void calculatePlayerLoyalty(List<GameLog> logs) {
        Map<String, Map<String, Integer>> serverPlayerTime = logs.stream()
                .filter(log -> log.eventType().equals("LOGOUT"))
                .collect(Collectors.groupingBy(GameLog::serverId,
                        Collectors.groupingBy(GameLog::playerId,
                                Collectors.summingInt(GameLog::value))));

        serverPlayerTime.forEach((serverId, playerTimes) -> {
            System.out.println(serverId + ":");
            playerTimes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> System.out.printf("%s - 플레이 시간: %d초%n", entry.getKey(), entry.getValue()));
            System.out.println();
        });
    }

    private static List<GameLog> generateSampleLogs() {
        return List.of(
                new GameLog(1623456789, "P001", "S1", "LOGIN", 0),
                new GameLog(1623456790, "P002", "S1", "LOGIN", 0),
                new GameLog(1623456800, "P001", "S1", "QUEST_START", 101),
                new GameLog(1623456900, "P002", "S1", "ITEM_PURCHASE", 50),
                new GameLog(1623457000, "P001", "S1", "QUEST_COMPLETE", 101),
                new GameLog(1623457100, "P001", "S1", "LOGOUT", 311),
                new GameLog(1623457200, "P003", "S2", "LOGIN", 0),
                new GameLog(1623457300, "P004", "S2", "LOGIN", 0),
                new GameLog(1623457400, "P003", "S2", "ITEM_PURCHASE", 100),
                new GameLog(1623457500, "P004", "S2", "QUEST_START", 102),
                new GameLog(1623457600, "P003", "S2", "LOGOUT", 400),
                new GameLog(1623457700, "P004", "S2", "LOGOUT", 400),
                new GameLog(1623457800, "P005", "S1", "LOGIN", 0),
                new GameLog(1623457900, "P005", "S1", "QUEST_START", 103),
                new GameLog(1623458000, "P005", "S1", "QUEST_COMPLETE", 103),
                new GameLog(1623458100, "P005", "S1", "LOGOUT", 300)
        );
    }
}

record GameLog(long timestamp, String playerId, String serverId, String eventType, int value) {}