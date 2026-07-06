package config

import (
	"bufio"
	"os"
	"path/filepath"
	"strings"
)

const (
	FatSecretOAuth2TokenURL  = "https://oauth.fatsecret.com/connect/token"
	FatSecretAPIBase         = "https://platform.fatsecret.com/rest"
	FatSecretRequestTokenURL = "https://authentication.fatsecret.com/oauth/request_token"
	FatSecretAuthorizeURL    = "https://authentication.fatsecret.com/oauth/authorize"
	FatSecretAccessTokenURL  = "https://authentication.fatsecret.com/oauth/access_token"
)

type Config struct {
	Root   string
	DBPath string
	Port   string
}

func Load() Config {
	root, err := os.Getwd()
	if err != nil {
		root = "."
	}
	port := strings.TrimSpace(os.Getenv("PORT"))
	if port == "" {
		port = "8787"
	}
	return Config{
		Root:   root,
		DBPath: filepath.Join(root, "personal_records.sqlite3"),
		Port:   port,
	}
}

func (c Config) LoadDotEnv() error {
	file, err := os.Open(filepath.Join(c.Root, ".env"))
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") || !strings.Contains(line, "=") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		key := strings.TrimSpace(parts[0])
		value := strings.Trim(strings.TrimSpace(parts[1]), `"'`)
		if os.Getenv(key) == "" {
			_ = os.Setenv(key, value)
		}
	}
	return scanner.Err()
}

func Env(name string) string {
	return strings.TrimSpace(os.Getenv(name))
}
