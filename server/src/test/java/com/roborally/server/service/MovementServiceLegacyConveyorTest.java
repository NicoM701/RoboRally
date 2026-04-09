package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.Board;
import com.roborally.server.model.ConveyorBelt;
import com.roborally.server.model.GameState;
import com.roborally.server.model.Robot;
import com.roborally.server.model.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementServiceLegacyConveyorTest {

    private MovementService movementService;
    private BoardLoader boardLoader;

    @BeforeEach
    void setUp() {
        movementService = new MovementService();
        boardLoader = new BoardLoader();
    }

    @Test
    void map1_curveChain_usesLegacyIncomingSideSemantics() {
        GameState game = new GameState("map1-curve-chain");
        game.setBoard(boardLoader.createDefaultBoard("map1"));

        Robot robot = new Robot(1L, 0, 0, 10, Direction.EAST);
        game.addRobot(1L, robot);

        movementService.executeStep(game, 0);

        assertEquals(1, robot.getX());
        assertEquals(10, robot.getY());
        assertEquals(Direction.NORTH, robot.getDirection(), "Arriving on the curve should rotate the robot north");

        movementService.executeStep(game, 1);

        assertEquals(1, robot.getX(), "The next move must follow the curve exit, not the stored incoming side");
        assertEquals(9, robot.getY());
        assertEquals(Direction.NORTH, robot.getDirection());
    }

    @Test
    void curveSourceTile_movesAlongItsLegacyExitDirection() {
        Board board = emptyBoard();
        ConveyorBelt curve = new ConveyorBelt(Direction.EAST, false);
        curve.setCurveRotation(RotationDirection.COUNTERCLOCKWISE);
        board.getTile(5, 5).setConveyorBelt(curve);

        GameState game = new GameState("synthetic-curve-source");
        game.setBoard(board);
        Robot robot = new Robot(1L, 0, 5, 5, Direction.SOUTH);
        game.addRobot(1L, robot);

        movementService.executeStep(game, 0);

        assertEquals(5, robot.getX());
        assertEquals(4, robot.getY(), "A legacy EAST+LEFT curve exits north in the remapped runtime");
        assertEquals(Direction.SOUTH, robot.getDirection(), "Source curves do not rotate the robot unless the destination belt does");
    }

    @Test
    void crossing_turnMatchesLegacyDestinationRules() {
        Board board = emptyBoard();
        board.getTile(5, 5).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false));

        ConveyorBelt crossing = new ConveyorBelt(Direction.WEST, false);
        crossing.setCrossingType("LEFT");
        board.getTile(5, 4).setConveyorBelt(crossing);

        GameState game = new GameState("synthetic-crossing-turn");
        game.setBoard(board);
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);

        movementService.executeStep(game, 0);

        assertEquals(5, robot.getX());
        assertEquals(4, robot.getY());
        assertEquals(Direction.WEST, robot.getDirection(), "Entering a LEFT crossing from its allowed side should rotate left");

        movementService.executeStep(game, 1);

        assertEquals(4, robot.getX());
        assertEquals(4, robot.getY());
        assertEquals(Direction.WEST, robot.getDirection());
    }

    private Board emptyBoard() {
        Board board = new Board("test", 12, 12);
        for (int x = 0; x < board.getWidth(); x++) {
            for (int y = 0; y < board.getHeight(); y++) {
                board.setTile(x, y, new Tile(x, y, FieldType.FLOOR));
            }
        }
        return board;
    }
}
