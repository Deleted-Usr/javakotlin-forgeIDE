#include "ParticleSystem.hpp"

#include "Math.hpp"

#include <algorithm>
#include <cmath>

namespace forge
{
    void ParticleSystem::burst(sf::Vector2f position, sf::Color color, int count)
    {
        for (int index = 0; index < count; ++index)
        {
            const float angle = random(0.f, math::pi * 2.f);
            const float speed = random(45.f, 230.f);
            particles_.push_back({position, {std::cos(angle) * speed, std::sin(angle) * speed},
                                  color, random(0.25f, 0.7f), random(2.f, 7.f)});
        }
    }

    void ParticleSystem::addTrail(sf::Vector2f position, sf::Color color)
    {
        color.a = 130;
        particles_.push_back({position, {}, color, 0.2f, 13.f});
    }

    void ParticleSystem::update(float seconds)
    {
        for (auto particle = particles_.begin(); particle != particles_.end();)
        {
            particle->life -= seconds;
            particle->position += particle->velocity * seconds;
            particle->velocity *= std::max(0.f, 1.f - seconds * 3.f);
            if (particle->life <= 0.f) particle = particles_.erase(particle);
            else ++particle;
        }
    }

    void ParticleSystem::draw(sf::RenderTarget& target) const
    {
        for (const auto& particle : particles_)
        {
            sf::CircleShape shape{particle.size, 8};
            shape.setOrigin({particle.size, particle.size});
            shape.setPosition(particle.position);
            sf::Color faded = particle.color;
            faded.a = static_cast<std::uint8_t>(std::clamp(particle.life * 400.f, 0.f, 255.f));
            shape.setFillColor(faded);
            target.draw(shape);
        }
    }

    void ParticleSystem::clear()
    {
        particles_.clear();
    }

    float ParticleSystem::random(float minimum, float maximum)
    {
        return std::uniform_real_distribution<float>{minimum, maximum}(randomEngine_);
    }
}
