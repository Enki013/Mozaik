package application

import "testing"

// Expected values come from the Worker's hashStr (worker/src/index.ts) run in Node.
func TestJSHashMatchesWorker(t *testing.T) {
	cases := map[string]string{
		"":                                "0",
		"a":                               "61",
		"Radiohead:Reckoner:1761739200":   "2bc26aaa",
		"Sezen Aksu:Gülümse:1700000000":   "1c5c697b",
		"Björk:Jóga 🎵:1234567890":         "4cfe06f2", // emoji is two UTF-16 code units
		"Daft Punk:Get Lucky:1761739200":  "6f6b2215",
		"Fleetwood Mac:Dreams:1761500000": "2f332a8a", // negative 32-bit hash
	}
	for in, want := range cases {
		if got := jsHash(in); got != want {
			t.Errorf("jsHash(%q) = %s, want %s", in, got, want)
		}
	}
	long := ""
	for i := 0; i < 500; i++ {
		long += "x"
	}
	if got := jsHash(long); got != "5a8be600" {
		t.Errorf("jsHash(500 x) = %s, want 5a8be600", got)
	}
}

func TestIsoMillisMatchesJavaScript(t *testing.T) {
	// new Date(1761739200 * 1000).toISOString()
	if got := isoMillis(1761739200); got != "2025-10-29T12:00:00.000Z" {
		t.Errorf("isoMillis = %s", got)
	}
}

func TestLastFMImagePrefersLargest(t *testing.T) {
	images := []any{
		map[string]any{"size": "small", "#text": "s.jpg"},
		map[string]any{"size": "large", "#text": "l.jpg"},
		map[string]any{"size": "extralarge", "#text": ""},
	}
	if got := lastFMImage(images); got != "l.jpg" {
		t.Errorf("lastFMImage = %q, want l.jpg", got)
	}
	if got := lastFMImage(nil); got != "" {
		t.Errorf("lastFMImage(nil) = %q", got)
	}
}
