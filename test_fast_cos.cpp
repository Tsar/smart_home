#include <iostream>
#include <math.h>

// Source: https://stackoverflow.com/a/28050328
double fastCos(double x) {
    constexpr double tp = 1. / (2. * M_PI);
    x *= tp;
    x -= 0.25 + std::floor(x + 0.25);
    x *= 16. * (std::abs(x) - 0.5);
    x += 0.225 * x * (std::abs(x) - 1.);
    return x;
}

int main() {
    double maxDelta = 0;
    double maxDeltaX = -1;

    for (double x = -M_PI; x <= M_PI; x += M_PI / 1000) {
        const double delta = std::abs(fastCos(x) - cos(x));
        std::cout << x << "\t" << cos(x) << "\t" << fastCos(x) << "\t" << delta << std::endl;
        if (delta > maxDelta) {
            maxDelta = delta;
            maxDeltaX = x;
        }
    }
    std::cout << std::endl << "maxDelta = " << maxDelta << ", reached on X = " << maxDeltaX << std::endl;

    return 0;
}
