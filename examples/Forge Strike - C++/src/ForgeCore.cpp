#include "ForgeCore.hpp"

#include "Palette.hpp"

#include <cmath>

namespace forge
{
    ForgeCore::ForgeCore(sf::Vector2f position)
        : position_(position)
    {
    }

    void ForgeCore::draw(sf::RenderTarget& target, float elapsed) const
    {
        for (int ring = 3; ring >= 0; --ring)
        {
            sf::CircleShape glow{radius + ring * 13.f, 48};
            glow.setOrigin({glow.getRadius(), glow.getRadius()});
            glow.setPosition(position_);
            glow.setFillColor(sf::Color{255, 112, 62, static_cast<std::uint8_t>(18 + (3 - ring) * 11)});
            target.draw(glow);
        }

        sf::CircleShape core{radius, 8};
        core.setOrigin({radius, radius});
        core.setPosition(position_);
        core.setRotation(sf::degrees(elapsed * 13.f));
        core.setFillColor(sf::Color{36, 30, 57});
        core.setOutlineThickness(7.f);
        core.setOutlineColor(palette::ember);
        target.draw(core);

        sf::CircleShape flame{24.f + std::sin(elapsed * 5.f) * 3.f, 7};
        flame.setOrigin({flame.getRadius(), flame.getRadius()});
        flame.setPosition(position_);
        flame.setFillColor(palette::gold);
        target.draw(flame);
    }

    void ForgeCore::damage(float amount) { health_ -= amount; }
    void ForgeCore::reset() { health_ = 100.f; }
    sf::Vector2f ForgeCore::position() const { return position_; }
    float ForgeCore::health() const { return health_; }
    bool ForgeCore::isDead() const { return health_ <= 0.f; }
}
