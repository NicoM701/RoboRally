package com.roborally.server.service;

import com.roborally.common.enums.CardType;
import com.roborally.server.model.ProgramCard;
import com.roborally.server.model.Robot;
import com.roborally.common.enums.Direction;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CardService.
 */
class CardServiceTest {

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardService();
    }

    // ─── Deck Creation ──────────────────────────────────

    @Test
    @DisplayName("Deck: exactly 84 cards")
    void createDeck_has84Cards() {
        List<ProgramCard> deck = cardService.createDeck();
        assertEquals(84, deck.size());
    }

    @Test
    @DisplayName("Deck: correct card type counts")
    void createDeck_correctTypeCounts() {
        List<ProgramCard> deck = cardService.createDeck();

        long uTurns = deck.stream().filter(c -> c.getType() == CardType.U_TURN).count();
        long turnLeft = deck.stream().filter(c -> c.getType() == CardType.TURN_LEFT).count();
        long turnRight = deck.stream().filter(c -> c.getType() == CardType.TURN_RIGHT).count();
        long backups = deck.stream().filter(c -> c.getType() == CardType.BACKUP).count();
        long move1 = deck.stream().filter(c -> c.getType() == CardType.MOVE_1).count();
        long move2 = deck.stream().filter(c -> c.getType() == CardType.MOVE_2).count();
        long move3 = deck.stream().filter(c -> c.getType() == CardType.MOVE_3).count();

        assertEquals(6, uTurns);
        assertEquals(18, turnLeft);
        assertEquals(18, turnRight);
        assertEquals(6, backups);
        assertEquals(18, move1);
        assertEquals(12, move2);
        assertEquals(6, move3);
    }

    @Test
    @DisplayName("Deck: all cards have unique IDs")
    void createDeck_uniqueIds() {
        List<ProgramCard> deck = cardService.createDeck();
        Set<Integer> ids = new HashSet<>();
        deck.forEach(c -> ids.add(c.getId()));
        assertEquals(84, ids.size());
    }

    @Test
    @DisplayName("Deck: all priorities are positive")
    void createDeck_positivePriorities() {
        List<ProgramCard> deck = cardService.createDeck();
        assertTrue(deck.stream().allMatch(c -> c.getPriority() > 0));
    }

    // ─── Dealing ────────────────────────────────────────

    @Test
    @DisplayName("Deal: 0 damage = 9 cards")
    void deal_noDamage_9Cards() {
        List<ProgramCard> deck = cardService.createDeck();
        cardService.shuffle(deck);
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        List<ProgramCard> hand = cardService.deal(deck, new ArrayList<>(), robot);

        assertEquals(9, hand.size());
        assertEquals(75, deck.size()); // 84 - 9
    }

    @Test
    @DisplayName("Deal: 3 damage = 6 cards")
    void deal_3Damage_6Cards() {
        List<ProgramCard> deck = cardService.createDeck();
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);
        robot.addDamage(3);

        List<ProgramCard> hand = cardService.deal(deck, new ArrayList<>(), robot);

        assertEquals(6, hand.size());
    }

    @Test
    @DisplayName("Deal: 9 damage = 0 cards")
    void deal_9Damage_0Cards() {
        List<ProgramCard> deck = cardService.createDeck();
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);
        robot.addDamage(9);

        List<ProgramCard> hand = cardService.deal(deck, new ArrayList<>(), robot);

        assertEquals(0, hand.size());
    }

    @Test
    @DisplayName("Deal: reshuffles discard when deck low")
    void deal_reshufflesDiscard() {
        List<ProgramCard> deck = new ArrayList<>();
        // Only 3 cards in deck
        deck.add(new ProgramCard(0, CardType.MOVE_1, 500));
        deck.add(new ProgramCard(1, CardType.MOVE_2, 700));
        deck.add(new ProgramCard(2, CardType.TURN_LEFT, 100));

        // 10 cards in discard
        List<ProgramCard> discard = new ArrayList<>();
        for (int i = 3; i < 13; i++) {
            discard.add(new ProgramCard(i, CardType.MOVE_1, 490 + i));
        }

        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        List<ProgramCard> hand = cardService.deal(deck, discard, robot);

        assertEquals(9, hand.size());
    }

    // ─── Validation ─────────────────────────────────────

    @Test
    @DisplayName("Validate: correct program → null (valid)")
    void validate_correctProgram() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);
        List<ProgramCard> hand = List.of(
                new ProgramCard(0, CardType.MOVE_1, 500),
                new ProgramCard(1, CardType.MOVE_2, 700),
                new ProgramCard(2, CardType.TURN_LEFT, 100),
                new ProgramCard(3, CardType.TURN_RIGHT, 200),
                new ProgramCard(4, CardType.MOVE_3, 800),
                new ProgramCard(5, CardType.BACKUP, 430),
                new ProgramCard(6, CardType.U_TURN, 10),
                new ProgramCard(7, CardType.MOVE_1, 510),
                new ProgramCard(8, CardType.MOVE_1, 520));
        List<ProgramCard> program = new ArrayList<>(hand.subList(0, 5));

        String error = cardService.validateProgram(robot, program, hand, 1);

        assertNull(error);
    }

    @Test
    @DisplayName("Validate: wrong size → error")
    void validate_wrongSize() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        String error = cardService.validateProgram(robot, List.of(), List.of(), 1);

        assertNotNull(error);
    }

    // ─── Collect Used Cards ─────────────────────────────

    @Test
    @DisplayName("Collect: non-blocked slots returned to discard")
    void collectUsedCards_returnsToDiscard() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);
        ProgramCard c1 = new ProgramCard(0, CardType.MOVE_1, 500);
        ProgramCard c2 = new ProgramCard(1, CardType.MOVE_2, 700);
        robot.setSlot(0, c1);
        robot.setSlot(1, c2);

        List<ProgramCard> discard = new ArrayList<>();
        cardService.collectUsedCards(discard, robot);

        assertEquals(2, discard.size()); // only non-null cards added to discard
    }
}
