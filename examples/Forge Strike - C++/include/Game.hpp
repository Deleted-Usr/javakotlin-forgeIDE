#pragma once

#include "Enemy.hpp"
#include "EnergyPickup.hpp"
#include "ForgeCore.hpp"
#include "ParticleSystem.hpp"
#include "Player.hpp"
#include "Projectile.hpp"

#include <SFML/Graphics.hpp>

#include <random>
#include <vector>

namespace forge
{
    /** Owns the window and coordinates the smaller gameplay classes. */
    class Game
    {
    public:
        Game();
        void run();

    private:
        enum class State { Playing, Paused, GameOver };

        void handleEvents();
        void update(float seconds);
        void updatePlayer(float seconds);
        void updateProjectiles(float seconds);
        void updateEnemies(float seconds);
        void updatePickups(float seconds);
        void spawnEnemy();
        void shoot();
        void restart();

        void render();
        void drawArena();
        void drawHud();
        void drawOverlay();

        [[nodiscard]] sf::Vector2f aimDirection() const;
        [[nodiscard]] float random(float minimum, float maximum);

        static constexpr sf::Vector2f viewSize{960.f, 540.f};
        static constexpr sf::Vector2f arenaSize{1'800.f, 1'100.f};

        sf::RenderWindow window_;
        sf::View worldView_;
        std::mt19937 randomEngine_{13};

        ParticleSystem particles_;
        Player player_;
        ForgeCore core_;
        std::vector<Projectile> projectiles_;
        std::vector<Enemy> enemies_;
        std::vector<EnergyPickup> pickups_;

        State state_{State::Playing};
        float shootCooldown_{};
        float spawnTimer_{};
        float elapsed_{};
        float titleTimer_{};
        bool dashRequested_{};
        int score_{};
        int wave_{1};
    };
}
