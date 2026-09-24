package fatsecret

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"

	"personal-records/internal/config"
)

type Client struct {
	cfg            config.Config
	http           *http.Client
	consumerKey    string
	consumerSecret string
}

func NewClient(cfg config.Config) *Client {
	return &Client{
		cfg:  cfg,
		http: &http.Client{Timeout: 30 * time.Second},
	}
}

func (c *Client) SetCredentials(key, secret string) {
	if c == nil {
		return
	}
	c.consumerKey = strings.TrimSpace(key)
	c.consumerSecret = strings.TrimSpace(secret)
}

func (c *Client) HasCredentials() bool {
	if c == nil {
		return false
	}
	return c.getConsumerKey() != "" && c.getConsumerSecret() != ""
}

func (c *Client) ConsumerKey() string {
	if c == nil {
		return ""
	}
	return c.getConsumerKey()
}

func (c *Client) ConsumerSecret() string {
	if c == nil {
		return ""
	}
	return c.getConsumerSecret()
}

func (c *Client) getConsumerKey() string {
	if c == nil {
		return config.Env("FATSECRET_CONSUMER_KEY")
	}
	if c.consumerKey != "" {
		return c.consumerKey
	}
	return config.Env("FATSECRET_CONSUMER_KEY")
}

func (c *Client) getConsumerSecret() string {
	if c == nil {
		return config.Env("FATSECRET_CONSUMER_SECRET")
	}
	if c.consumerSecret != "" {
		return c.consumerSecret
	}
	return config.Env("FATSECRET_CONSUMER_SECRET")
}

func (c *Client) PublicSearch(query string) (map[string]any, error) {
	form := url.Values{}
	form.Set("grant_type", "client_credentials")
	form.Set("scope", "basic")
	req, err := http.NewRequest(http.MethodPost, config.FatSecretOAuth2TokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	clientID := config.Env("FATSECRET_CLIENT_ID")
	if clientID == "" {
		clientID = c.getConsumerKey()
	}
	clientSecret := config.Env("FATSECRET_CLIENT_SECRET")
	if clientSecret == "" {
		clientSecret = c.getConsumerSecret()
	}
	req.SetBasicAuth(clientID, clientSecret)
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("FatSecret token HTTP %d: %s", resp.StatusCode, string(body))
	}
	var token struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(body, &token); err != nil {
		return nil, err
	}
	endpoint := config.FatSecretAPIBase + "/foods/search/v1"
	values := url.Values{"search_expression": {query}, "format": {"json"}}
	req, err = http.NewRequest(http.MethodGet, endpoint+"?"+values.Encode(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+token.AccessToken)
	resp, err = c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, _ = io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("FatSecret search HTTP %d: %s", resp.StatusCode, string(body))
	}
	var payload map[string]any
	return payload, json.Unmarshal(body, &payload)
}

func (c *Client) RequestToken(callback string) (url.Values, error) {
	params := map[string]string{"oauth_callback": callback}
	signed := c.oauth1Parameters(http.MethodPost, config.FatSecretRequestTokenURL, params, "")
	return c.formRequest(http.MethodPost, config.FatSecretRequestTokenURL, signed)
}

func (c *Client) AccessToken(oauthToken, verifier, tokenSecret string) (url.Values, error) {
	params := map[string]string{"oauth_token": oauthToken, "oauth_verifier": verifier}
	signed := c.oauth1Parameters(http.MethodGet, config.FatSecretAccessTokenURL, params, tokenSecret)
	return c.formRequest(http.MethodGet, config.FatSecretAccessTokenURL, signed)
}

func (c *Client) SignedGet(endpoint string, params map[string]string, token, tokenSecret string) (map[string]any, error) {
	params = clone(params)
	params["oauth_token"] = token
	signed := c.oauth1Parameters(http.MethodGet, endpoint, params, tokenSecret)
	values := url.Values{}
	for key, value := range signed {
		values.Set(key, value)
	}
	req, err := http.NewRequest(http.MethodGet, endpoint+"?"+values.Encode(), nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("FatSecret HTTP %d: %s", resp.StatusCode, string(body))
	}
	var payload map[string]any
	return payload, json.Unmarshal(body, &payload)
}

func (c *Client) formRequest(method, endpoint string, form map[string]string) (url.Values, error) {
	values := url.Values{}
	for key, value := range form {
		values.Set(key, value)
	}
	requestURL := endpoint
	var body io.Reader
	if method == http.MethodGet {
		requestURL += "?" + values.Encode()
	} else {
		body = strings.NewReader(values.Encode())
	}
	req, err := http.NewRequest(method, requestURL, body)
	if err != nil {
		return nil, err
	}
	if method == http.MethodPost {
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("FatSecret OAuth HTTP %d: %s", resp.StatusCode, string(data))
	}
	return url.ParseQuery(string(data))
}

func (c *Client) oauth1Parameters(method, endpoint string, params map[string]string, tokenSecret string) map[string]string {
	oauth := map[string]string{
		"oauth_consumer_key":     c.getConsumerKey(),
		"oauth_nonce":            nonce(),
		"oauth_signature_method": "HMAC-SHA1",
		"oauth_timestamp":        fmt.Sprintf("%d", time.Now().Unix()),
		"oauth_version":          "1.0",
	}
	for key, value := range params {
		if strings.HasPrefix(key, "oauth_") {
			oauth[key] = value
		}
	}
	all := clone(params)
	for key, value := range oauth {
		all[key] = value
	}
	keys := make([]string, 0, len(all))
	for key := range all {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, key := range keys {
		parts = append(parts, percent(key)+"="+percent(all[key]))
	}
	base := strings.Join([]string{strings.ToUpper(method), percent(endpoint), percent(strings.Join(parts, "&"))}, "&")
	signingKey := percent(c.getConsumerSecret()) + "&" + percent(tokenSecret)
	mac := hmac.New(sha1.New, []byte(signingKey))
	_, _ = mac.Write([]byte(base))
	oauth["oauth_signature"] = base64.StdEncoding.EncodeToString(mac.Sum(nil))
	result := clone(params)
	for key, value := range oauth {
		result[key] = value
	}
	return result
}

func percent(value string) string {
	return strings.ReplaceAll(url.QueryEscape(value), "+", "%20")
}

func nonce() string {
	bytes := make([]byte, 18)
	if _, err := rand.Read(bytes); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(bytes)
}

func clone(input map[string]string) map[string]string {
	output := map[string]string{}
	for key, value := range input {
		output[key] = value
	}
	return output
}
