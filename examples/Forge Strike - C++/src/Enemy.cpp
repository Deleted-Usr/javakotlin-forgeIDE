#include "Enemy.hpp"

#include "Math.hpp"
#include "Palette.hpp"

#include <algorithm>
#include <cmath>

namespace forge
{
    Enemy::Enemy(sf::Vector2f position, float radius, float health, float phase)
        : position_(position), radius_(radius), health_(health), phase_(phase)
    {
    }

    void Enemy::update(float seconds, sf::Vector2f playerPosition, sf::Vector2f corePosition, int wave)
    {
        phase_ += seconds * 5.f;
        const bool chasePlayer = math::distanceSquared(position_, playerPosition) < 300.f * 300.f;
        const sf::Vector2f target = chasePlayer ? playerPosition : corePosition;
        const sf::Vector2f desired = math::normalised(target - position_) * (78.f + wave * 4.f);
        velocity_ += (desired - velocity_) * std::min(1.f, seconds * 4.f);
        position_ += velocity_ * seconds;
    }

    void Enemy::draw(sf::RenderTarget& target, sf::Vector2f playerPosition) const
    {
        sf::CircleShape shadow{radius_, 20};
        shadow.setOrigin({radius_, radius_});
        shadow.setScale({1.15f, 0.45f});
        shadow.setPosition(position_ + sf::Vector2f{4.f, radius_ * 0.8f});
        shadow.setFillColor(sf::Color{13, 12, 29, 100});
        target.draw(shadow);

        sf::CircleShape body{radius_, 7};
        body.setOrigin({radius_, radius_});
        body.setPosition(position_ + sf::Vector2f{0.f, std::sin(phase_) * 2.f});
        body.setRotation(sf::degrees(phase_ * 10.f));
        body.setFillColor(palette::mint);
        body.setOutlineThickness(3.f);
        body.setOutlineColor(sf::Color{34, 67, 66});
        target.draw(body);

        const sf::Vector2f facing = math::normalised(playerPosition - position_);
        sf::CircleShape eye{4.f, 12};
        eye.setOrigin({4.f, 4.f});
        eye.setPosition(position_ + facing * (radius_ * 0.48f));
        eye.setFillColor(palette::night);
        target.draw(eye);
    }

    void Enemy::damage(float amount) { health_ -= amount; }
    sf::Vector2f Enemy::position() const { return position_; }
    float Enemy::radius() const { return radius_; }
    bool Enemy::isDead() const { return health_ <= 0.f; }
}
