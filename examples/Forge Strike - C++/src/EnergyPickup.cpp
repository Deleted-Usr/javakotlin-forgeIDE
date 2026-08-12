#include "EnergyPickup.hpp"

#include "Math.hpp"
#include "Palette.hpp"

#include <cmath>

namespace forge
{
    EnergyPickup::EnergyPickup(sf::Vector2f position, float phase)
        : position_(position), phase_(phase)
    {
    }

    void EnergyPickup::update(float seconds, sf::Vector2f playerPosition)
    {
        phase_ += seconds * 4.f;
        const sf::Vector2f delta = playerPosition - position_;
        if (math::lengthSquared(delta) < 125.f * 125.f)
            position_ += math::normalised(delta) * seconds * 230.f;
    }

    void EnergyPickup::draw(sf::RenderTarget& target) const
    {
        const float pulse = 7.f + std::sin(phase_) * 2.f;
        sf::CircleShape glow{pulse + 9.f, 14};
        glow.setOrigin({glow.getRadius(), glow.getRadius()});
        glow.setPosition(position_);
        glow.setFillColor(sf::Color{255, 199, 62, 50});
        target.draw(glow);

        sf::CircleShape crystal{pulse, 4};
        crystal.setOrigin({pulse, pulse});
        crystal.setPosition(position_);
        crystal.setRotation(sf::degrees(phase_ * 20.f));
        crystal.setFillColor(palette::gold);
        target.draw(crystal);
    }

    sf::Vector2f EnergyPickup::position() const { return position_; }

    bool EnergyPickup::touches(sf::Vector2f playerPosition) const
    {
        return math::distanceSquared(position_, playerPosition) < 28.f * 28.f;
    }
}
