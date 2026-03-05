package com.roborally.server.service;

import com.roborally.common.enums.CardType;
import com.roborally.server.model.ProgramCard;
import com.roborally.server.model.Robot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Manages the 84-card program card deck.
 * Official RoboRally distribution:
 * Move 1: 18 cards (490-660, step 10)
 * Move 2: 12 cards (670-780, step 10)
 * Move 3: 6 cards (790-840, step 10)
 * Backup: 6 cards (430-480, step 10)
 * Turn Left: 18 cards (70-420, step 20)
 * Turn Right: 18 cards (80-420, step 20)
 * U-Turn: 6 cards (10-60, step 10)
 * Total: 84 cards
 */
@Component
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    /**
     * Create a full 84-card deck with official priorities.
     */
    public List<ProgramCard> createDeck() {
        List<ProgramCard> deck = new ArrayList<>();
        int id = 0;

        // U-Turn: 6 cards, priorities 10-60
        for (int p = 10; p <= 60; p += 10) {
            deck.add(new ProgramCard(id++, CardType.U_TURN, p));
        }

        // Turn Left: 18 cards, priorities 70-420 step 20
        for (int p = 70; p <= 420; p += 20) {
            deck.add(new ProgramCard(id++, CardType.TURN_LEFT, p));
        }

        // Turn Right: 18 cards, priorities 80-420 step 20
        for (int p = 80; p <= 420; p += 20) {
            deck.add(new ProgramCard(id++, CardType.TURN_RIGHT, p));
        }

        // Backup: 6 cards, priorities 430-480
        for (int p = 430; p <= 480; p += 10) {
            deck.add(new ProgramCard(id++, CardType.BACKUP, p));
        }

        // Move 1: 18 cards, priorities 490-660
        for (int p = 490; p <= 660; p += 10) {
            deck.add(new ProgramCard(id++, CardType.MOVE_1, p));
        }

        // Move 2: 12 cards, priorities 670-780
        for (int p = 670; p <= 780; p += 10) {
            deck.add(new ProgramCard(id++, CardType.MOVE_2, p));
        }

        // Move 3: 6 cards, priorities 790-840
        for (int p = 790; p <= 840; p += 10) {
            deck.add(new ProgramCard(id++, CardType.MOVE_3, p));
        }

        log.info("Created deck with {} cards", deck.size());
        return deck;
    }

    /**
     * Shuffle the deck.
     */
    public void shuffle(List<ProgramCard> deck) {
        Collections.shuffle(deck);
    }

    /**
     * Deal cards to a robot: 9 - damage tokens.
     * If hand is all rotation cards, redeal.
     */
    public List<ProgramCard> deal(List<ProgramCard> deck, List<ProgramCard> discardPile, Robot robot) {
        int count = robot.getCardsToReceive();
        if (count <= 0)
            return List.of();

        // Reshuffle discard into deck if needed
        if (deck.size() < count) {
            deck.addAll(discardPile);
            discardPile.clear();
            shuffle(deck);
            log.info("Reshuffled discard pile into deck ({} cards)", deck.size());
        }

        List<ProgramCard> hand;
        int attempts = 0;
        do {
            hand = new ArrayList<>();
            for (int i = 0; i < count && !deck.isEmpty(); i++) {
                hand.add(deck.remove(0));
            }
            attempts++;
            // Redeal if hand is all rotation cards (no movement at all)
            if (hand.stream().allMatch(c -> c.getType().isRotation())) {
                log.info("Redeal #{} for player {} - all rotation cards", attempts, robot.getPlayerId());
                deck.addAll(hand);
                shuffle(deck);
            } else {
                break;
            }
        } while (attempts < 3); // Prevent infinite loops

        return hand;
    }

    /**
     * Validate a submitted program for a robot.
     * Returns error message or null if valid.
     */
    public String validateProgram(Robot robot, List<ProgramCard> program, List<ProgramCard> hand, int round) {
        if (program == null || program.size() != 5) {
            return "Programm muss genau 5 Karten enthalten.";
        }

        int blockedSlots = robot.getBlockedSlots();
        int openSlots = 5 - blockedSlots;

        // Check that open slots contain valid cards from the hand
        for (int i = 0; i < openSlots; i++) {
            ProgramCard card = program.get(i);
            if (card == null)
                return "Slot " + (i + 1) + " darf nicht leer sein.";
            if (!hand.contains(card))
                return "Karte nicht in der Hand.";
        }

        // First card of first round must be a movement card
        if (round == 1 && program.get(0) != null && program.get(0).getType().isRotation()) {
            return "Die erste Karte in Runde 1 muss eine Bewegungskarte sein.";
        }

        return null; // Valid
    }

    /**
     * Collect used (non-blocked) cards back to discard.
     */
    public void collectUsedCards(List<ProgramCard> discardPile, Robot robot) {
        int blocked = robot.getBlockedSlots();
        for (int i = 0; i < 5 - blocked; i++) {
            ProgramCard card = robot.getSlot(i);
            if (card != null) {
                discardPile.add(card);
                robot.setSlot(i, null);
            }
        }
    }
}
