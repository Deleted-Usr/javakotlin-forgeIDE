#include "Projectile.hpp"

#include "Palette.hpp"

namespace forge
{
    Projectile::Projectile(sf::Vector2f position, sf::Vector2f velocity)
        : position_(position), velocity_(velocity)
    {
    }

    void Projectile::update(float seconds)
    {
        position_ += velocity_ * seconds;
        life_ -= seconds;
    }

    void Projectile::draw(sf::RenderTarget& target) const
    {
        sf::CircleShape glow{10.f, 12};
        glow.setOrigin({10.f, 10.f});
        glow.setPosition(position_);
        glow.setFillColor(sf::Color{255, 202, 80, 55});
        target.draw(glow);

        sf::CircleShape shot{4.f, 10};
        shot.setOrigin({4.f, 4.f});
        shot.setPosition(position_);
        shot.setFillColor(palette::gold);
        target.draw(shot);
    }

    sf::Vector2f Projectile::position() const { return position_; }

    bool Projectile::hasExpired(sf::Vector2f arenaSize) const
    {
        return life_ <= 0.f || position_.x < 0.f || position_.y < 0.f
            || position_.x > arenaSize.x || position_.y > arenaSize.y;
    }
}
