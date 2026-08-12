#include "Player.hpp"

#include "Math.hpp"
#include "Palette.hpp"
#include "ParticleSystem.hpp"

#include <algorithm>
#include <cmath>

namespace forge
{
    Player::Player(sf::Vector2f spawnPosition)
        : spawnPosition_(spawnPosition), position_(spawnPosition)
    {
    }

    void Player::update(float seconds, sf::Vector2f input, bool dashRequested,
                        sf::Vector2f arenaSize, ParticleSystem& particles)
    {
        dashCooldown_ = std::max(0.f, dashCooldown_ - seconds);
        if (math::lengthSquared(input) > 0.f)
        {
            input = math::normalised(input);
            lastMoveDirection_ = input;
        }

        if (dashRequested && dashCooldown_ <= 0.f)
        {
            dashTime_ = 0.14f;
            dashCooldown_ = 0.85f;
            velocity_ = lastMoveDirection_ * 780.f;
            particles.burst(position_, palette::ember, 18);
        }

        if (dashTime_ > 0.f)
        {
            dashTime_ -= seconds;
            trailTimer_ -= seconds;
            if (trailTimer_ <= 0.f)
            {
                particles.addTrail(position_, palette::ember);
                trailTimer_ = 0.025f;
            }
        }
        else
        {
            const sf::Vector2f target = input * 260.f;
            velocity_ += (target - velocity_) * std::min(1.f, seconds * 11.f);
        }

        position_ += velocity_ * seconds;
        position_.x = std::clamp(position_.x, radius, arenaSize.x - radius);
        position_.y = std::clamp(position_.y, radius, arenaSize.y - radius);
    }

    void Player::draw(sf::RenderTarget& target, sf::Vector2f aimDirection, float weaponReadiness) const
    {
        const float angle = std::atan2(aimDirection.y, aimDirection.x) * 180.f / math::pi;

        if (dashTime_ > 0.f)
        {
            for (int trail = 1; trail <= 4; ++trail)
            {
                sf::CircleShape echo{radius - trail * 2.f, 8};
                echo.setOrigin({echo.getRadius(), echo.getRadius()});
                echo.setPosition(position_ - lastMoveDirection_ * static_cast<float>(trail * 14));
                echo.setFillColor(sf::Color{255, 126, 68, static_cast<std::uint8_t>(80 - trail * 13)});
                target.draw(echo);
            }
        }

        sf::CircleShape body{radius, 8};
        body.setOrigin({radius, radius});
        body.setPosition(position_);
        body.setRotation(sf::degrees(angle + 22.5f));
        body.setFillColor(palette::coral);
        body.setOutlineThickness(3.f);
        body.setOutlineColor(palette::gold);
        target.draw(body);

        sf::RectangleShape barrel{{28.f, 8.f}};
        barrel.setOrigin({2.f, 4.f});
        barrel.setPosition(position_ + aimDirection * 7.f);
        barrel.setRotation(sf::degrees(angle));
        barrel.setFillColor(palette::gold);
        target.draw(barrel);

        sf::CircleShape readiness{radius + 7.f, 28};
        readiness.setOrigin({readiness.getRadius(), readiness.getRadius()});
        readiness.setPosition(position_);
        readiness.setFillColor(sf::Color::Transparent);
        readiness.setOutlineThickness(2.f);
        readiness.setOutlineColor(sf::Color{255, 218, 91, static_cast<std::uint8_t>(
                40 + 150 * std::clamp(weaponReadiness, 0.f, 1.f))});
        target.draw(readiness);
    }

    void Player::reset()
    {
        position_ = spawnPosition_;
        velocity_ = {};
        lastMoveDirection_ = {1.f, 0.f};
        health_ = 100.f;
        dashCooldown_ = 0.f;
        dashTime_ = 0.f;
        trailTimer_ = 0.f;
    }

    void Player::damage(float amount) { health_ -= amount; }
    void Player::heal(float amount) { health_ = std::min(100.f, health_ + amount); }
    void Player::applyRecoil(sf::Vector2f direction) { velocity_ -= direction * 16.f; }
    sf::Vector2f Player::position() const { return position_; }
    float Player::health() const { return health_; }
    float Player::dashReadiness() const { return 1.f - dashCooldown_ / 0.85f; }
    bool Player::isDead() const { return health_ <= 0.f; }
}
