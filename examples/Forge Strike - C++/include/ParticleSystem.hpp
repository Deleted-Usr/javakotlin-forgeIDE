#pragma once

#include <SFML/Graphics.hpp>

#include <random>
#include <vector>

namespace forge
{
    class ParticleSystem
    {
    public:
        void burst(sf::Vector2f position, sf::Color color, int count);
        void addTrail(sf::Vector2f position, sf::Color color);
        void update(float seconds);
        void draw(sf::RenderTarget& target) const;
        void clear();

    private:
        struct Particle
        {
            sf::Vector2f position;
            sf::Vector2f velocity;
            sf::Color color;
            float life{};
            float size{};
        };

        [[nodiscard]] float random(float minimum, float maximum);

        std::vector<Particle> particles_;
        std::mt19937 randomEngine_{7};
    };
}
