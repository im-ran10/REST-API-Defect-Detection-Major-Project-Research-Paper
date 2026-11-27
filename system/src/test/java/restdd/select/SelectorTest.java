package restdd.select;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorTest {

    private static final String SPEC = """
{
 "openapi": "3.0.3",
 "info": {
  "title": "shelf",
  "version": "1"
 },
 "security": [
  {
   "key": []
  }
 ],
 "components": {
  "securitySchemes": {
   "key": {
    "type": "apiKey",
    "in": "header",
    "name": "K"
   }
  }
 },
 "paths": {
  "/books": {
   "get": {
    "parameters": [
     {
      "name": "search",
      "in": "query",
      "schema": {
       "type": "string"
      }
     },
     {
      "name": "limit",
      "in": "query",
      "schema": {
       "type": "integer"
      }
     }
    ],
    "responses": {
     "200": {
      "description": "ok",
      "content": {
       "application/json": {
        "schema": {
         "type": "array",
         "items": {
          "type": "object"
         }
        }
       }
      }
     }
    }
   },
   "post": {
    "requestBody": {
     "content": {
      "application/json": {
       "schema": {
        "type": "object",
        "properties": {
         "title": {
          "type": "string",
          "maxLength": 50
         }
        }
       }
      }
     }
    },
    "responses": {
     "201": {
      "description": "created"
     }
    }
   }
  },
  "/books/{id}": {
   "get": {
    "parameters": [
     {
      "name": "id",
      "in": "path",
      "required": true,
      "schema": {
       "type": "integer"
      }
     }
    ],
    "responses": {
     "200": {
      "description": "ok"
     }
    }
   },
   "put": {
    "parameters": [
     {
      "name": "id",
      "in": "path",
      "required": true,
      "schema": {
       "type": "integer"
      }
     }
    ],
    "requestBody": {
     "content": {
      "application/json": {
       "schema": {
        "type": "object"
       }
      },
      "multipart/form-data": {
       "schema": {
        "type": "object"
       }
      }
     }
    },
    "responses": {
     "200": {
      "description": "ok"
     }
    }
   },
   "delete": {
    "parameters": [
     {
      "name": "id",
      "in": "path",
      "required": true,
      "schema": {
       "type": "integer"
      }
     }
    ],
    "responses": {
     "204": {
      "description": "gone"
     }
    }
   }
  },
  "/books/{id}/archive": {
   "post": {
    "parameters": [
     {
      "name": "id",
      "in": "path",
      "required": true,
      "schema": {
       "type": "integer"
      }
     }
    ],
    "responses": {
     "200": {
      "description": "ok"
     }
    }
   }
  },
  "/books/{id}/cover": {
   "post": {
    "parameters": [
     {
      "name": "id",
      "in": "path",
      "required": true,
      "schema": {
       "type": "integer"
      }
     }
    ],
    "requestBody": {
     "content": {
      "multipart/form-data": {
       "schema": {
        "type": "object"
       }
      }
     }
    },
    "responses": {
     "200": {
      "description": "ok"
     }
    }
   }
  },
  "/status": {
   "get": {
    "security": [],
    "responses": {
     "200": {
      "description": "ok"
     }
    }
   }
  }
 }
}
""";

    private static Map<String, OperationFeatures> features(boolean secondRole) throws Exception {
        Path f = Files.createTempFile("shelf", ".json");
        Files.writeString(f, SPEC);
        NormalisedSpec spec = SpecNormaliser.normalise(f);
        return FeatureExtractor.extract(spec, null, secondRole);
    }

    @Test
    void predicatesFollowTheProtocolRules() throws Exception {
        Map<String, OperationFeatures> f = features(false);
        Catalogue b = Catalogue.load("B");
        Catalogue c = Catalogue.load("C");
        assertTrue(Selector.fired(c, f.get("GET /books"), false).containsAll(List.of("C2", "C4", "C8")));
        assertTrue(Selector.fired(b, f.get("GET /books"), false).containsAll(List.of("B10", "B11", "B12", "B13", "B14")));
        assertTrue(Selector.fired(c, f.get("POST /books"), false).containsAll(List.of("C1", "C3", "C6")));
        assertTrue(Selector.fired(b, f.get("POST /books"), false).contains("B7"));
        assertTrue(Selector.fired(b, f.get("DELETE /books/{id}"), false).contains("B6"));
        assertTrue(Selector.fired(c, f.get("POST /books/{id}/archive"), false).contains("C6"));
        assertTrue(Selector.fired(c, f.get("POST /books/{id}/cover"), false).contains("C10"));
        assertFalse(Selector.fired(c, f.get("PUT /books/{id}"), false).contains("C10"), "JSON body offered also as multipart");
        assertFalse(Selector.fired(c, f.get("GET /books/{id}"), false).contains("C7"), "no second role");
        assertTrue(Selector.fired(c, features(true).get("GET /books/{id}"), false).contains("C7"));
        assertEquals(List.of(), Selector.fired(c, f.get("GET /status"), false));
        assertFalse(Selector.fired(b, f.get("GET /status"), false).contains("B8"), "operation-level security: [] overrides");
    }

    @Test
    void allocationRotatesBySeedAndFallsBackToTheDefault() throws Exception {
        Map<String, OperationFeatures> f = features(false);
        Catalogue c = Catalogue.load("C");
        List<String> fired = Selector.fired(c, f.get("GET /books"), false);
        List<Selector.Slot> s0 = Selector.assign(c, f.get("GET /books"), 0, 3, false);
        List<Selector.Slot> s1 = Selector.assign(c, f.get("GET /books"), 1, 3, false);
        assertEquals(fired.get(0), s0.get(0).entryId());
        assertEquals(fired.get(1 % fired.size()), s1.get(0).entryId());
        List<Selector.Slot> t1 = Selector.assign(c, f.get("GET /books"), 1, 3, false, Selector.Start.TILE);
        assertEquals(fired.get(3 % fired.size()), t1.get(0).entryId(), "TILE: seed 1 starts after the first block of 3");
        List<Selector.Slot> d = Selector.assign(c, f.get("GET /status"), 7, 3, false);
        assertTrue(d.stream().allMatch(x -> x.entryId().equals("C9")));
        String task = Selector.task(c, s0);
        assertTrue(task.contains("SLOT 1 - ") && task.contains("Oracle: ") && !task.contains("Defects4REST"), task);
    }

    @Test
    void catalogueTextIsDefectBlindAndTokenParityHolds() throws Exception {
        List<String> banned = List.of("dolibarr", "restcountries", "podman", "nocodb", "awx", "netbox", "mastodon",
                "kafka", "flowable", "seaweed", "thirdpart", "invoice", "proposal", "container", "countr");
        double[] mean = new double[2];
        int i = 0;
        for (String id : List.of("B", "C")) {
            Catalogue cat = Catalogue.load(id);
            double sum = 0;
            for (Catalogue.Entry e : cat.entries()) {
                String text = String.join(" ", e.name(), e.applicability(), e.probe(), e.oracle(), e.example()).toLowerCase();
                for (String w : banned) {
                    assertFalse(text.contains(w), id + " " + e.id() + " mentions " + w);
                }
                sum += text.length() / 4.0;
            }
            mean[i++] = sum / cat.entries().size();
        }
        double ratio = mean[1] / mean[0];
        assertTrue(ratio > 0.8 && ratio < 1.2, "token parity C/B " + ratio);
        var overlap = new ObjectMapper().readTree(Catalogue.class.getResourceAsStream("/catalogues/overlap.json"));
        for (String id : List.of("B", "C")) {
            for (Catalogue.Entry e : Catalogue.load(id).entries()) {
                assertTrue(overlap.path(id).has(e.id()), "overlap table lacks " + e.id());
            }
        }
    }
}
