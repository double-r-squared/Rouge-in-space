#include <iostream>
#include <string>
#include <vector>
#include <unordered_map>
#include <limits>

using namespace std;

// Function to build the shift table for Horspool's algorithm
std::unordered_map<char, int> buildShiftTable(const std::string &pattern) {
    std::unordered_map<char, int> shiftTable;
    int m = pattern.size();

    // Default shift for all characters not in pattern
    for (unsigned char c = 0; c < std::numeric_limits<unsigned char>::max(); ++c) {
        shiftTable[c] = m; 
    }

    // Fill shift values for characters in pattern except the last one
    for (int i = 0; i < m - 1; ++i) {
        shiftTable[pattern[i]] = m - 1 - i;
    }
    
    return shiftTable;
}

// Horspool's search function
int horspoolSearch(const std::string &text, const std::string &pattern) {
    int n = text.size();
    int m = pattern.size();

    if (m == 0) return 0; // Empty pattern matches at index 0
    if (m > n) return -1; // Pattern longer than text

    auto shiftTable = buildShiftTable(pattern);
    
    for(auto &p : shiftTable) { cout << p.first << ":" << p.second; } 
    int i = m - 1; // Index in text

    while (i < n) {
        cout << text[i] << "\n";
        int k = 0;
        // Compare pattern from right to left
        while (k < m && pattern[m - 1 - k] == text[i - k]) {
            k++;
        }
        if (k == m) {
            return i - m + 1; // Match found
        } else {
            // Shift according to the mismatched character
            char mismatchedChar = text[i];
            i += shiftTable[mismatchedChar];
        }
    }
    return -1; // No match found
}

int main() {
    std::string text, pattern;

    std::cout << "Enter the text: ";
    std::getline(std::cin, text);

    std::cout << "Enter the pattern to search: ";
    std::getline(std::cin, pattern);

    // Input validation
    if (text.empty() || pattern.empty()) {
        std::cerr << "Error: Text and pattern must not be empty.\n";
        return 1;
    }

    int position = horspoolSearch(text, pattern);

    if (position != -1) {
        std::cout << "Pattern found at index: " << position << "\n";
    } else {
        std::cout << "Pattern not found.\n";
    }

    return 0;
}

