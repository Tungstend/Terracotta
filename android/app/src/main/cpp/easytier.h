//
// Created by hanji on 2025/7/27.
//

#ifndef TERRACOTTA_EASYTIER_H
#define TERRACOTTA_EASYTIER_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

struct KeyValuePair {
    const char* key;
    const char* value;
};

void get_error_msg(const char** out);
void free_string(const char* s);
int parse_config(const char* cfg_str);
int run_network_instance(const char* cfg_str);
int collect_network_infos(KeyValuePair* infos, size_t max_length);

#ifdef __cplusplus
}
#endif

#endif //TERRACOTTA_EASYTIER_H
