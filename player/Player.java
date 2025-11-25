package com.mineshaft.player;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Entity;
import com.mineshaft.world.World;
import static org.lwjgl.glfw.GLFW.*;

public class Player extends Entity {
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_HEIGHT_SNEAKING = 1.5f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float EYE_HEIGHT_SNEAKING = 1.54f;

    // Physics constants
    private static final float GRAVITY = -0.08f;
    private static final float WATER_GRAVITY = -0.02f;
    private static final float JUMP_STRENGTH = 0.50f;
    private static final float WATER_SWIM_SPEED = 0.40f; // INCREASED: Stronger swim
    private static final float WATER_JUMP_BOOST = 0.60f; // INCREASED: Stronger jump from ground in water
    private static final float TERMINAL_VELOCITY = -3.92f;
    private static final float WATER_TERMINAL_VELOCITY = -0.08f;
    private static final float MAX_WATER_UP_SPEED = 0.65f; // INCREASED: Allow faster upward movement

    // Movement constants
    private static final float STEP_HEIGHT = 0.6f;
    private static final float GROUND_SNAP_DISTANCE = 0.1f;
    private static final float SNEAK_SPEED_MULTIPLIER = 0.3f;

    // CRITICAL: Collision settings like Minecraft
    private static final float COLLISION_TOLERANCE = 0.001f;

    private final long window;
    private World world;
    private Inventory inventory;

    // Game state
    private GameMode gameMode = GameMode.CREATIVE;
    private boolean flying = true;
    private boolean inWater = false;
    private boolean headInWater = false;
    private boolean sprinting = false;
    private boolean sneaking = false;

    // Input state
    private boolean inputForward;
    private boolean inputBack;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputJump;
    private boolean inputSneak;
    private boolean inputSprint;

    // Smooth stepping
    private float stepOffsetY = 0;

    public Player(World world, long window) {
        super(null);
        this.world = world;
        this.window = window;
        this.inventory = new Inventory();
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }

    public void updateInput() {
        inputForward = glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
        inputBack = glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS;
        inputLeft = glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS;
        inputRight = glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS;
        inputJump = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
        inputSneak = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
        inputSprint = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
    }

    @Override
    public void tick() {
        super.tick();

        checkWaterStatus();

        if (stepOffsetY > 0) {
            stepOffsetY -= 0.2f;
            if (stepOffsetY < 0)
                stepOffsetY = 0;
        }

        if (gameMode == GameMode.SPECTATOR) {
            handleSpectatorMovement();
        } else if (flying) {
            handleFlyingMovement();
        } else {
            handleNormalMovement();
        }

        // CRITICAL: Resolve any collision overlap (pushback system)
        resolveCollisions();
    }

    private void handleSpectatorMovement() {
        float speed = (Settings.WALK_SPEED * 2.0f) / Settings.TARGET_TPS;
        if (inputSprint)
            speed *= 2.0f;

        float moveX = 0, moveZ = 0, moveY = 0;

        if (inputForward) {
            moveX += (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (inputBack) {
            moveX -= (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ += (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (inputLeft) {
            moveX += (float) Math.sin(Math.toRadians(yaw - 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw - 90)) * speed;
        }
        if (inputRight) {
            moveX += (float) Math.sin(Math.toRadians(yaw + 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw + 90)) * speed;
        }

        if (inputJump)
            moveY += speed;
        if (inputSneak)
            moveY -= speed;

        x += moveX;
        y += moveY;
        z += moveZ;

        velocityX = moveX;
        velocityY = moveY;
        velocityZ = moveZ;
    }

    private void handleFlyingMovement() {
        float speed = Settings.WALK_SPEED / Settings.TARGET_TPS;
        if (inputSprint)
            speed = Settings.SPRINT_SPEED / Settings.TARGET_TPS;

        float moveX = 0, moveZ = 0, moveY = 0;

        if (inputForward) {
            moveX += (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (inputBack) {
            moveX -= (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ += (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (inputLeft) {
            moveX += (float) Math.sin(Math.toRadians(yaw - 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw - 90)) * speed;
        }
        if (inputRight) {
            moveX += (float) Math.sin(Math.toRadians(yaw + 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw + 90)) * speed;
        }

        if (inputJump)
            moveY += speed;
        if (inputSneak)
            moveY -= speed;

        if (!isColliding(x + moveX, y, z + moveZ)) {
            x += moveX;
            z += moveZ;
        }
        if (!isColliding(x, y + moveY, z)) {
            y += moveY;
        }

        velocityX = moveX;
        velocityY = moveY;
        velocityZ = moveZ;

        sneaking = false;
    }

    private void handleNormalMovement() {
        sneaking = inputSneak;

        float speed = Settings.WALK_SPEED / Settings.TARGET_TPS;

        if (inputSprint && !sneaking && inputForward) {
            speed = Settings.SPRINT_SPEED / Settings.TARGET_TPS;
            sprinting = true;
        } else {
            sprinting = false;
        }

        if (sneaking) {
            speed *= SNEAK_SPEED_MULTIPLIER;
        }

        if (inWater) {
            speed *= 0.5f;
        }

        float moveX = 0, moveZ = 0;

        if (inputForward) {
            moveX += (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (inputBack) {
            moveX -= (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ += (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (inputLeft) {
            moveX += (float) Math.sin(Math.toRadians(yaw - 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw - 90)) * speed;
        }
        if (inputRight) {
            moveX += (float) Math.sin(Math.toRadians(yaw + 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw + 90)) * speed;
        }

        if (moveX != 0 || moveZ != 0) {
            moveWithCollisionAndStep(moveX, moveZ);
        }

        velocityX = x - prevX;
        velocityZ = z - prevZ;

        if (inWater) {
            applyWaterPhysics();
        } else {
            applyGravityPhysics();
        }
    }

    private void applyWaterPhysics() {
        if (inputJump) {
            if (onGround) {
                velocityY = WATER_JUMP_BOOST;
                onGround = false;
            } else {
                velocityY = WATER_SWIM_SPEED;
            }
        }

        velocityY += WATER_GRAVITY;

        if (velocityY < WATER_TERMINAL_VELOCITY) {
            velocityY = WATER_TERMINAL_VELOCITY;
        }

        if (velocityY > MAX_WATER_UP_SPEED) {
            velocityY = MAX_WATER_UP_SPEED;
        }

        float newY = y + velocityY;

        if (velocityY <= 0) {
            if (isOnGroundCheck(x, newY, z)) {
                float groundY = getGroundYBelow(x, newY, z);
                if (groundY > 0) {
                    y = groundY;
                    velocityY = 0;
                    onGround = true;
                } else {
                    y = newY;
                    onGround = false;
                }
            } else {
                y = newY;
                onGround = false;
            }
        } else {
            if (isCeilingCollision(x, newY, z)) {
                velocityY = 0;
            } else {
                y = newY;
            }
            onGround = false;
        }
    }

    private void applyGravityPhysics() {
        if (inputJump && onGround && !sneaking) {
            velocityY = JUMP_STRENGTH;
            onGround = false;
        }

        if (onGround && velocityY == 0) {
            float groundY = getGroundYBelow(x, y, z);
            if (groundY > 0 && Math.abs(y - groundY) < GROUND_SNAP_DISTANCE) {
                y = groundY;
            } else if (groundY > 0 && Math.abs(y - groundY) > GROUND_SNAP_DISTANCE) {
                onGround = false;
            }
        }

        velocityY += GRAVITY;

        if (velocityY < TERMINAL_VELOCITY) {
            velocityY = TERMINAL_VELOCITY;
        }

        float newY = y + velocityY;

        if (velocityY <= 0) {
            if (isOnGroundCheck(x, newY, z)) {
                float groundY = getGroundYBelow(x, newY, z);

                if (groundY > 0) {
                    y = groundY;
                    velocityY = 0;
                    onGround = true;
                    return;
                }
            }
        } else {
            if (isCeilingCollision(x, newY, z)) {
                velocityY = 0;
                newY = y;
            }
        }

        y = newY;
        onGround = false;
    }

    /**
     * CRITICAL FIX: Enhanced water stepping - easier to climb out of water
     * Allow stepping even with slight downward velocity in water
     */
    private void moveWithCollisionAndStep(float dx, float dz) {
        if (world == null) {
            x += dx;
            z += dz;
            return;
        }

        float newX = x + dx;
        float newZ = z + dz;

        if (!isColliding(newX, y, newZ)) {
            x = newX;
            z = newZ;
            return;
        }

        // CRITICAL: More forgiving stepping conditions
        // In water: can step even when falling slightly (velocityY down to -0.1)
        // This allows climbing even when gravity is pulling down
        boolean canStep;
        if (inWater) {
            // In water: very forgiving - can step almost anytime when jumping
            canStep = !sneaking && inputJump && velocityY >= -0.1f;
        } else {
            // On land: normal stepping
            canStep = !sneaking && onGround && velocityY <= 0;
        }

        if (canStep) {
            float optimalStep = findOptimalStepHeight(newX, newZ);

            if (optimalStep > 0 && optimalStep <= STEP_HEIGHT) {
                float targetY = y + optimalStep;

                if (!isColliding(newX, targetY, newZ)) {
                    x = newX;
                    z = newZ;
                    y = targetY;
                    stepOffsetY = optimalStep;
                    onGround = true;

                    // CRITICAL: Big boost when stepping from water
                    // This helps player continue climbing
                    if (inWater) {
                        velocityY = Math.max(velocityY, 0.2f);
                    }

                    return;
                }
            }
        }

        float oldX = x;
        float oldZ = z;

        boolean movedX = false;
        boolean movedZ = false;

        if (!isColliding(newX, y, oldZ)) {
            x = newX;
            movedX = true;
        }

        if (!isColliding(oldX, y, newZ)) {
            z = newZ;
            movedZ = true;
        }

        if (!movedX && !movedZ && (dx != 0 || dz != 0)) {
            if (Math.abs(dx) > Math.abs(dz)) {
                if (!isColliding(oldX + dx * 0.9f, y, oldZ)) {
                    x = oldX + dx * 0.9f;
                }
            } else {
                if (!isColliding(oldX, y, oldZ + dz * 0.9f)) {
                    z = oldZ + dz * 0.9f;
                }
            }

            if (x == oldX && z == oldZ) {
                float perpX = oldX - dz * 0.3f;
                float perpZ = oldZ + dx * 0.3f;
                if (!isColliding(perpX, y, perpZ)) {
                    x = perpX;
                    z = perpZ;
                }
            }
        }
    }

    /**
     * CRITICAL: Pushback system - if player is inside a block, push them out
     * This is called every tick to ensure player never gets stuck inside blocks
     */
    private void resolveCollisions() {
        if (world == null || gameMode == GameMode.SPECTATOR) {
            return;
        }

        float halfWidth = PLAYER_WIDTH / 2;
        float currentHeight = getCurrentHeight();

        // Get player bounds
        float playerMinX = x - halfWidth;
        float playerMaxX = x + halfWidth;
        float playerMinY = y;
        float playerMaxY = y + currentHeight;
        float playerMinZ = z - halfWidth;
        float playerMaxZ = z + halfWidth;

        // Get block range to check
        int minBlockX = (int) Math.floor(playerMinX);
        int maxBlockX = (int) Math.floor(playerMaxX);
        int minBlockY = (int) Math.floor(playerMinY);
        int maxBlockY = (int) Math.floor(playerMaxY);
        int minBlockZ = (int) Math.floor(playerMinZ);
        int maxBlockZ = (int) Math.floor(playerMaxZ);

        // Check all potentially colliding blocks
        for (int bx = minBlockX; bx <= maxBlockX; bx++) {
            for (int by = minBlockY; by <= maxBlockY; by++) {
                for (int bz = minBlockZ; bz <= maxBlockZ; bz++) {
                    GameBlock block = world.getBlock(bx, by, bz);

                    if (block.isSolid()) {
                        // Block bounds
                        float blockMinX = bx;
                        float blockMaxX = bx + 1.0f;
                        float blockMinY = by;
                        float blockMaxY = by + 1.0f;
                        float blockMinZ = bz;
                        float blockMaxZ = bz + 1.0f;

                        // Calculate overlap on each axis
                        float overlapX = Math.min(playerMaxX - blockMinX, blockMaxX - playerMinX);
                        float overlapY = Math.min(playerMaxY - blockMinY, blockMaxY - playerMinY);
                        float overlapZ = Math.min(playerMaxZ - blockMinZ, blockMaxZ - playerMinZ);

                        // Check if there's actual overlap
                        if (overlapX > 0 && overlapY > 0 && overlapZ > 0) {
                            // Push out on the axis with smallest overlap
                            if (overlapX < overlapY && overlapX < overlapZ) {
                                // Push on X axis
                                if (x < bx + 0.5f) {
                                    x = blockMinX - halfWidth - COLLISION_TOLERANCE;
                                } else {
                                    x = blockMaxX + halfWidth + COLLISION_TOLERANCE;
                                }
                            } else if (overlapY < overlapZ) {
                                // Push on Y axis
                                if (y < by + 0.5f) {
                                    y = blockMinY - currentHeight - COLLISION_TOLERANCE;
                                } else {
                                    y = blockMaxY + COLLISION_TOLERANCE;
                                    onGround = true;
                                }
                                velocityY = 0;
                            } else {
                                // Push on Z axis
                                if (z < bz + 0.5f) {
                                    z = blockMinZ - halfWidth - COLLISION_TOLERANCE;
                                } else {
                                    z = blockMaxZ + halfWidth + COLLISION_TOLERANCE;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private float findOptimalStepHeight(float newX, float newZ) {
        float minStep = 0.0f;
        float maxStep = STEP_HEIGHT;
        float epsilon = 0.05f;

        if (!isColliding(newX, y + maxStep, newZ)) {
            while (maxStep - minStep > epsilon) {
                float midStep = (minStep + maxStep) / 2.0f;

                if (isColliding(newX, y + midStep, newZ)) {
                    minStep = midStep;
                } else {
                    maxStep = midStep;
                }
            }

            return maxStep;
        }

        return 0;
    }

    private float getGroundYBelow(float px, float py, float pz) {
        if (world == null)
            return -1;

        float halfWidth = PLAYER_WIDTH / 2;

        int minX = (int) Math.floor(px - halfWidth);
        int maxX = (int) Math.floor(px + halfWidth);
        int minZ = (int) Math.floor(pz - halfWidth);
        int maxZ = (int) Math.floor(pz + halfWidth);

        int startY = (int) Math.floor(py);
        int searchDepth = 3;

        int highestGroundY = -1;

        for (int checkY = startY; checkY >= startY - searchDepth && checkY >= 0; checkY--) {
            for (int bx = minX; bx <= maxX; bx++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    GameBlock block = world.getBlock(bx, checkY, bz);

                    if (block.isSolid()) {
                        highestGroundY = Math.max(highestGroundY, checkY);
                    }
                }
            }

            if (highestGroundY != -1) {
                break;
            }
        }

        if (highestGroundY != -1) {
            return highestGroundY + 1.0f;
        }

        return -1;
    }

    private void checkWaterStatus() {
        if (world == null) {
            inWater = false;
            headInWater = false;
            return;
        }

        int checkX = (int) Math.floor(x);
        int checkZ = (int) Math.floor(z);

        int headY = (int) Math.floor(y + getCurrentEyeHeight() - 0.1f);
        GameBlock headBlock = world.getBlock(checkX, headY, checkZ);
        headInWater = (headBlock == BlockRegistry.WATER);

        int bodyY = (int) Math.floor(y + getCurrentHeight() * 0.5f);
        GameBlock bodyBlock = world.getBlock(checkX, bodyY, checkZ);
        inWater = (bodyBlock == BlockRegistry.WATER);
    }

    /**
     * CRITICAL: Proper collision detection matching Minecraft
     * Player bounding box: 0.6 x 1.8 (width x height)
     * Position is at center bottom of player
     */
    public boolean isColliding(float px, float py, float pz) {
        if (world == null)
            return false;

        float halfWidth = PLAYER_WIDTH / 2;
        float currentHeight = getCurrentHeight();

        // Player bounds (exact, no shrinking)
        float playerMinX = px - halfWidth;
        float playerMaxX = px + halfWidth;
        float playerMinY = py;
        float playerMaxY = py + currentHeight;
        float playerMinZ = pz - halfWidth;
        float playerMaxZ = pz + halfWidth;

        // Get block coordinates (expand by 1 to catch edge cases)
        int minX = (int) Math.floor(playerMinX);
        int maxX = (int) Math.floor(playerMaxX);
        int minY = (int) Math.floor(playerMinY);
        int maxY = (int) Math.floor(playerMaxY);
        int minZ = (int) Math.floor(playerMinZ);
        int maxZ = (int) Math.floor(playerMaxZ);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    GameBlock block = world.getBlock(bx, by, bz);
                    if (block.isSolid()) {
                        // Block bounds
                        float blockMinX = bx;
                        float blockMaxX = bx + 1.0f;
                        float blockMinY = by;
                        float blockMaxY = by + 1.0f;
                        float blockMinZ = bz;
                        float blockMaxZ = bz + 1.0f;

                        // AABB overlap test
                        boolean overlapX = playerMaxX > blockMinX && playerMinX < blockMaxX;
                        boolean overlapY = playerMaxY > blockMinY && playerMinY < blockMaxY;
                        boolean overlapZ = playerMaxZ > blockMinZ && playerMinZ < blockMaxZ;

                        if (overlapX && overlapY && overlapZ) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isCeilingCollision(float px, float py, float pz) {
        return isColliding(px, py, pz);
    }

    private boolean isOnGroundCheck(float px, float py, float pz) {
        if (world == null)
            return false;

        float halfWidth = PLAYER_WIDTH / 2;

        int minX = (int) Math.floor(px - halfWidth);
        int maxX = (int) Math.floor(px + halfWidth);
        int minY = (int) Math.floor(py - 0.05f);
        int maxY = (int) Math.floor(py);
        int minZ = (int) Math.floor(pz - halfWidth);
        int maxZ = (int) Math.floor(pz + halfWidth);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    GameBlock block = world.getBlock(bx, by, bz);
                    if (block.isSolid()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public float getRenderY(float partialTicks) {
        return super.getRenderY(partialTicks) + stepOffsetY;
    }

    public float getCurrentHeight() {
        return sneaking ? PLAYER_HEIGHT_SNEAKING : PLAYER_HEIGHT;
    }

    public float getCurrentEyeHeight() {
        return sneaking ? EYE_HEIGHT_SNEAKING : EYE_HEIGHT;
    }

    public float getEyeY() {
        return y + getCurrentEyeHeight();
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
        this.flying = (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void toggleFlying() {
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            flying = !flying;
        }
    }

    public boolean isInWater() {
        return inWater;
    }

    public boolean isHeadInWater() {
        return headInWater;
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isSprinting() {
        return sprinting;
    }
}