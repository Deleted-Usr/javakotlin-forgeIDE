#include "Game.hpp"

#include "Math.hpp"
#include "Palette.hpp"

#include <algorithm>
#include <iostream>
#include <sstream>

namespace
{
    void drawBar(sf::RenderTarget& target, sf::Vector2f position, sf::Vector2f size,
                 float fraction, sf::Color color)
    {
        sf::RectangleShape background{size};
        background.setPosition(position);
        background.setFillColor(sf::Color{55, 51, 78, 235});
        target.draw(background);

        sf::RectangleShape fill{{size.x * std::clamp(fraction, 0.f, 1.f), size.y}};
        fill.setPosition(position);
        fill.setFillColor(color);
        target.draw(fill);
    }
}

namespace forge
{
    Game::Game()
        : window_(sf::VideoMode({960u, 540u}), "Forge Strike | Wave 1 | Score 0"),
          worldView_(sf::FloatRect({0.f, 0.f}, viewSize)),
          player_(arenaSize / 2.f + sf::Vector2f{0.f, 150.f}),
          core_(arenaSize / 2.f)
    {
        window_.setVerticalSyncEnabled(true);
        window_.setKeyRepeatEnabled(false);
    }

    void Game::run()
    {
        sf::Clock clock;
        float accumulator = 0.f;
        constexpr float step = 1.f / 120.f;

        std::cout << "Forge Strike started. Protect the core and collect energy.\n";

        while (window_.isOpen())
        {
            handleEvents();
            accumulator += std::min(clock.restart().asSeconds(), 0.05f);

            while (accumulator >= step)
            {
                update(step);
                accumulator -= step;
            }
            render();
        }
    }

    void Game::handleEvents()
    {
        while (const auto event = window_.pollEvent())
        {
            if (event->is<sf::Event::Closed>()) window_.close();

            const auto* key = event->getIf<sf::Event::KeyPressed>();
            if (!key) continue;

            if (key->code == sf::Keyboard::Key::Escape) window_.close();
            else if (key->code == sf::Keyboard::Key::R) restart();
            else if (key->code == sf::Keyboard::Key::P && state_ != State::GameOver)
                state_ = state_ == State::Paused ? State::Playing : State::Paused;
            else if ((key->code == sf::Keyboard::Key::LShift || key->code == sf::Keyboard::Key::RShift)
                     && state_ == State::Playing)
                dashRequested_ = true;
        }
    }

    void Game::update(float seconds)
    {
        titleTimer_ -= seconds;
        if (titleTimer_ <= 0.f)
        {
            std::ostringstream title;
            title << "Forge Strike | Wave " << wave_ << " | Score " << score_;
            if (state_ == State::Paused) title << " | PAUSED";
            if (state_ == State::GameOver) title << " | CORE LOST - R to restart";
            window_.setTitle(title.str());
            titleTimer_ = 0.2f;
        }

        if (state_ != State::Playing)
        {
            dashRequested_ = false;
            return;
        }

        elapsed_ += seconds;
        wave_ = 1 + static_cast<int>(elapsed_ / 22.f);
        shootCooldown_ = std::max(0.f, shootCooldown_ - seconds);
        spawnTimer_ -= seconds;

        updatePlayer(seconds);
        updateProjectiles(seconds);
        updateEnemies(seconds);
        updatePickups(seconds);
        particles_.update(seconds);

        if ((sf::Mouse::isButtonPressed(sf::Mouse::Button::Left)
             || sf::Keyboard::isKeyPressed(sf::Keyboard::Key::Space)) && shootCooldown_ <= 0.f)
            shoot();

        if (spawnTimer_ <= 0.f)
        {
            spawnEnemy();
            spawnTimer_ = std::max(0.24f, 1.05f - wave_ * 0.075f);
        }

        if (player_.isDead() || core_.isDead())
        {
            state_ = State::GameOver;
            particles_.burst(core_.isDead() ? core_.position() : player_.position(), palette::coral, 80);
            std::cout << "Run ended on wave " << wave_ << " with score " << score_ << ".\n";
        }
    }

    void Game::updatePlayer(float seconds)
    {
        sf::Vector2f input;
        if (sf::Keyboard::isKeyPressed(sf::Keyboard::Key::A)
            || sf::Keyboard::isKeyPressed(sf::Keyboard::Key::Left)) input.x -= 1.f;
        if (sf::Keyboard::isKeyPressed(sf::Keyboard::Key::D)
            || sf::Keyboard::isKeyPressed(sf::Keyboard::Key::Right)) input.x += 1.f;
        if (sf::Keyboard::isKeyPressed(sf::Keyboard::Key::W)
            || sf::Keyboard::isKeyPressed(sf::Keyboard::Key::Up)) input.y -= 1.f;
        if (sf::Keyboard::isKeyPressed(sf::Keyboard::Key::S)
            || sf::Keyboard::isKeyPressed(sf::Keyboard::Key::Down)) input.y += 1.f;

        player_.update(seconds, input, dashRequested_, arenaSize, particles_);
        dashRequested_ = false;

        const sf::Vector2f halfView = viewSize / 2.f;
        sf::Vector2f camera = player_.position();
        camera.x = std::clamp(camera.x, halfView.x, arenaSize.x - halfView.x);
        camera.y = std::clamp(camera.y, halfView.y, arenaSize.y - halfView.y);
        worldView_.setCenter(camera);
    }

    void Game::updateProjectiles(float seconds)
    {
        for (auto& projectile : projectiles_) projectile.update(seconds);

        for (auto projectile = projectiles_.begin(); projectile != projectiles_.end();)
        {
            bool hit = false;
            for (auto enemy = enemies_.begin(); enemy != enemies_.end() && !hit;)
            {
                const float collisionRadius = enemy->radius() + 6.f;
                if (math::distanceSquared(projectile->position(), enemy->position())
                    < collisionRadius * collisionRadius)
                {
                    enemy->damage(34.f);
                    hit = true;
                    particles_.burst(projectile->position(), palette::gold, 5);

                    if (enemy->isDead())
                    {
                        const sf::Vector2f defeatedAt = enemy->position();
                        score_ += 10;
                        if (random(0.f, 1.f) < 0.38f)
                            pickups_.emplace_back(defeatedAt, random(0.f, math::pi * 2.f));
                        particles_.burst(defeatedAt, palette::mint, 18);
                        enemy = enemies_.erase(enemy);
                    }
                    else ++enemy;
                }
                else ++enemy;
            }

            if (hit || projectile->hasExpired(arenaSize)) projectile = projectiles_.erase(projectile);
            else ++projectile;
        }
    }

    void Game::updateEnemies(float seconds)
    {
        for (auto enemy = enemies_.begin(); enemy != enemies_.end();)
        {
            enemy->update(seconds, player_.position(), core_.position(), wave_);
            const float playerCollision = enemy->radius() + Player::radius;
            const float coreCollision = enemy->radius() + ForgeCore::radius;

            if (math::distanceSquared(enemy->position(), player_.position())
                < playerCollision * playerCollision)
            {
                player_.damage(16.f);
                particles_.burst(player_.position(), palette::coral, 14);
                enemy = enemies_.erase(enemy);
            }
            else if (math::distanceSquared(enemy->position(), core_.position())
                     < coreCollision * coreCollision)
            {
                core_.damage(11.f);
                particles_.burst(enemy->position(), palette::coral, 14);
                enemy = enemies_.erase(enemy);
            }
            else ++enemy;
        }
    }

    void Game::updatePickups(float seconds)
    {
        for (auto pickup = pickups_.begin(); pickup != pickups_.end();)
        {
            pickup->update(seconds, player_.position());
            if (pickup->touches(player_.position()))
            {
                player_.heal(8.f);
                score_ += 5;
                particles_.burst(pickup->position(), palette::gold, 12);
                pickup = pickups_.erase(pickup);
            }
            else ++pickup;
        }
    }

    void Game::spawnEnemy()
    {
        sf::Vector2f position;
        switch (static_cast<int>(random(0.f, 4.f)))
        {
            case 0: position = {random(10.f, arenaSize.x - 10.f), 12.f}; break;
            case 1: position = {arenaSize.x - 12.f, random(10.f, arenaSize.y - 10.f)}; break;
            case 2: position = {random(10.f, arenaSize.x - 10.f), arenaSize.y - 12.f}; break;
            default: position = {12.f, random(10.f, arenaSize.y - 10.f)}; break;
        }

        const float radius = random(16.f, 25.f);
        enemies_.emplace_back(position, radius, radius > 21.f ? 68.f : 42.f,
                              random(0.f, math::pi * 2.f));
    }

    void Game::shoot()
    {
        const sf::Vector2f direction = aimDirection();
        projectiles_.emplace_back(player_.position() + direction * 25.f, direction * 720.f);
        player_.applyRecoil(direction);
        shootCooldown_ = 0.13f;
        particles_.burst(player_.position() + direction * 23.f, palette::gold, 3);
    }

    void Game::restart()
    {
        projectiles_.clear();
        enemies_.clear();
        pickups_.clear();
        particles_.clear();
        player_.reset();
        core_.reset();
        shootCooldown_ = 0.f;
        spawnTimer_ = 0.f;
        elapsed_ = 0.f;
        score_ = 0;
        wave_ = 1;
        state_ = State::Playing;
    }

    void Game::render()
    {
        window_.clear(palette::night);
        window_.setView(worldView_);
        drawArena();
        core_.draw(window_, elapsed_);
        for (const auto& pickup : pickups_) pickup.draw(window_);
        for (const auto& enemy : enemies_) enemy.draw(window_, player_.position());
        for (const auto& projectile : projectiles_) projectile.draw(window_);
        particles_.draw(window_);
        player_.draw(window_, aimDirection(), 1.f - shootCooldown_ / 0.13f);
        drawHud();
        if (state_ != State::Playing) drawOverlay();
        window_.display();
    }

    void Game::drawArena()
    {
        sf::RectangleShape floor{arenaSize};
        floor.setFillColor(palette::dusk);
        window_.draw(floor);

        sf::RectangleShape line;
        line.setFillColor(sf::Color{102, 83, 139, 75});
        for (float x = 0.f; x <= arenaSize.x; x += 80.f)
        {
            line.setPosition({x, 0.f});
            line.setSize({2.f, arenaSize.y});
            window_.draw(line);
        }
        for (float y = 0.f; y <= arenaSize.y; y += 80.f)
        {
            line.setPosition({0.f, y});
            line.setSize({arenaSize.x, 2.f});
            window_.draw(line);
        }

        sf::RectangleShape border{arenaSize - sf::Vector2f{12.f, 12.f}};
        border.setPosition({6.f, 6.f});
        border.setFillColor(sf::Color::Transparent);
        border.setOutlineThickness(6.f);
        border.setOutlineColor(palette::ember);
        window_.draw(border);
    }

    void Game::drawHud()
    {
        const sf::View previous = window_.getView();
        window_.setView(sf::View(sf::FloatRect({0.f, 0.f}, viewSize)));

        sf::RectangleShape panel{{258.f, 104.f}};
        panel.setPosition({20.f, 18.f});
        panel.setFillColor(sf::Color{18, 18, 42, 215});
        panel.setOutlineThickness(2.f);
        panel.setOutlineColor(sf::Color{91, 73, 119, 220});
        window_.draw(panel);

        sf::CircleShape playerIcon{8.f, 8};
        playerIcon.setPosition({36.f, 36.f});
        playerIcon.setFillColor(palette::coral);
        window_.draw(playerIcon);
        drawBar(window_, {60.f, 38.f}, {194.f, 12.f}, player_.health() / 100.f, palette::coral);

        sf::CircleShape coreIcon{8.f, 6};
        coreIcon.setPosition({36.f, 65.f});
        coreIcon.setFillColor(palette::ember);
        window_.draw(coreIcon);
        drawBar(window_, {60.f, 67.f}, {194.f, 12.f}, core_.health() / 100.f, palette::ember);

        sf::RectangleShape dashIcon{{14.f, 5.f}};
        dashIcon.setPosition({37.f, 97.f});
        dashIcon.setFillColor(palette::gold);
        window_.draw(dashIcon);
        drawBar(window_, {60.f, 94.f}, {194.f, 10.f}, player_.dashReadiness(), palette::gold);
        window_.setView(previous);
    }

    void Game::drawOverlay()
    {
        const sf::View previous = window_.getView();
        window_.setView(sf::View(sf::FloatRect({0.f, 0.f}, viewSize)));

        sf::RectangleShape shade{viewSize};
        shade.setFillColor(sf::Color{10, 9, 25, 185});
        window_.draw(shade);

        if (state_ == State::Paused)
        {
            for (float x : {430.f, 505.f})
            {
                sf::RectangleShape pause{{25.f, 110.f}};
                pause.setPosition({x, 215.f});
                pause.setFillColor(palette::gold);
                window_.draw(pause);
            }
        }
        else
        {
            for (float angle : {45.f, -45.f})
            {
                sf::RectangleShape slash{{145.f, 24.f}};
                slash.setOrigin({72.5f, 12.f});
                slash.setPosition(viewSize / 2.f);
                slash.setRotation(sf::degrees(angle));
                slash.setFillColor(palette::coral);
                window_.draw(slash);
            }
        }
        window_.setView(previous);
    }

    sf::Vector2f Game::aimDirection() const
    {
        const sf::Vector2f mouse = window_.mapPixelToCoords(sf::Mouse::getPosition(window_), worldView_);
        return math::normalised(mouse - player_.position());
    }

    float Game::random(float minimum, float maximum)
    {
        return std::uniform_real_distribution<float>{minimum, maximum}(randomEngine_);
    }
}
