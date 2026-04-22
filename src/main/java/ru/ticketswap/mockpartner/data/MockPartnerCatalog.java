package ru.ticketswap.mockpartner.data;

import java.time.Instant;
import java.util.List;

public final class MockPartnerCatalog {

    private static final List<MockPartnerEventData> EVENTS = List.of(
            new MockPartnerEventData(
                    1001,
                    "Концерт Imagine Dragons",
                    Instant.parse("2030-09-10T16:00:00Z"),
                    "org1",
                    "ВТБ Арена",
                    "Москва, Ленинградский проспект",
                    "Europe/Moscow"
            ),
            new MockPartnerEventData(
                    1002,
                    "Концерт Arctic Monkeys",
                    Instant.parse("2030-11-22T18:30:00Z"),
                    "org1",
                    "МТС Live Холл",
                    "Москва, шоссе Энтузиастов",
                    "Europe/Moscow"
            ),
            new MockPartnerEventData(
                    1003,
                    "Фестиваль Кино под звездами",
                    Instant.parse("2031-06-15T17:00:00Z"),
                    "org1",
                    "Приморская сцена Open Air",
                    "Сочи, Приморская набережная",
                    "Europe/Moscow"
            ),
            new MockPartnerEventData(
                    2001,
                    "Мировой тур Coldplay",
                    Instant.parse("2030-10-04T19:00:00Z"),
                    "org2",
                    "Газпром Арена",
                    "Санкт Петербург, Футбольная аллея",
                    "Europe/Moscow"
            ),
            new MockPartnerEventData(
                    2002,
                    "Вечерний концерт The Weeknd",
                    Instant.parse("2031-02-14T20:15:00Z"),
                    "org2",
                    "СКА Арена",
                    "Санкт Петербург, проспект Юрия Гагарина",
                    "Europe/Moscow"
            ),
            new MockPartnerEventData(
                    2003,
                    "Музыкальная выставка Звук будущего",
                    Instant.parse("2031-08-29T09:45:00Z"),
                    "org2",
                    "Казань Экспо",
                    "Казань, Выставочная улица",
                    "Europe/Moscow"
            )
    );

    private MockPartnerCatalog() {
    }

    public static List<MockPartnerEventData> events() {
        return EVENTS;
    }
}
