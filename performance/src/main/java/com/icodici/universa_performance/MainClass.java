package com.icodici.universa_performance;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;

public class MainClass {
    public static class Pair<First, Second> {
        private First first;
        private Second second;

        public static <T,U> Pair of(T first, U second) {
            return new Pair(first,second);
        }

        public Pair(First first, Second second) {
            this.first = first;
            this.second = second;
        }

        public void setFirst(First first) {
            this.first = first;
        }

        public void setSecond(Second second) {
            this.second = second;
        }

        public First getFirst() {
            return first;
        }

        public Second getSecond() {
            return second;
        }

        public void set(First first, Second second) {
            setFirst(first);
            setSecond(second);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Pair pair = (Pair) o;

            if (first != null ? !first.equals(pair.first) : pair.first != null) return false;
            if (second != null ? !second.equals(pair.second) : pair.second != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = first != null ? first.hashCode() : 0;
            result = 31 * result + (second != null ? second.hashCode() : 0);
            return result;
        }
    }

    public static class TestKeys {

        static public PrivateKey privateKey(int index) throws EncryptionError {
            byte[] src = Do.decodeBase64(binaryKeys[index]);
            return new PrivateKey(src);
        }


        public static String binaryKeys[] = new String[]{
                "JgAcAQABvIDxdLhF++STfUSbcnF7BQD2FsByvNaznuyf3zerGbsNuMgPjc45vnQIlCdGYUADNv/ZD9ZXMVJ7sqU7tuGDwW1Y9KItOpOBfojs/1Ke650543wuvWamkMpojVQpXfO8NmSnWBjm7m8LxgWQf2jZrjlAEkWZ4y+5MCcYTg2UMt9PebyA2I1WqmtpWA5fFhGrxy/LsldTnQVqi85AxHTOXrRlEjGuhMyWHOeA5l7M/GzeW7HWBsRM37amX46UdTcHyCV2rFu2tPgzxhA1qif0u6mU3rUTtqq+p+u2BWWVzj+NcVLOb49ip3CI4WCu1prmfZrI9g8RvOsKWJKMGBn+GzP2aok=",
                "JgAcAQABvIDtlV347of+oF+5mWPYnhGbABLHpp6/eD7WAlZee+hnjI4lh0sEaOEyz9ajBUgEqkw19IsJL1JwmwngK2rE9E9ZOWlvOAQzzHm6t4iY4/BK+TBdk3n3G5xm2wltS2Eg32ivu9r0kjCrl6Ud6LaNHsTfacklvP3S2aGHL5nYrbc7MbyAxcUgBFq8JJecR3eB/9uZjXvu/88Bvy2QH9UP1Xw8eYptFcNelZ45bj0AhAJAiDikNeF748iGFqPXzxkNUXyIx1FH7OdtkXSlG5864gt8Tsgmr4aQDBhJJXSmmg/kS5ieRhl8z3IBhPIujYKUHF0Nm2TZu7u7fHYi+4CcOvzLvs0=",
                "JgAcAQABvIDjqoZBjQ0G1cfljjqpYZB6GT28udRk8DzJ9Vkh60H8BBjhiXEoNFDfX6Lv0rGT+Vj+aHpVx3WA8AoyyRx1hrEilOnNxXsU+Njz1xlXthSU+Yc5aSBQF9NmIdPE/CUIxpZ9jwxjFuS7xzuSvKMwZHQE4fOsV341QwWXYs+h5jvPibyAvM2j/YRWf68Dc9kyRCrTFpWYbIFwd4wthxMtHgVgcLVIlQKtay5ar63Vxkvzchqol7AHJanxtJbpKNQ9S4lJhJZgPFhznNjGD7r/g0U6psdxcct/eJjiL4gMBu/A8VmBOUgFsrOCAb6ataOKACIDSI3kz4i9vmB8tyVG6VbMvlU=",
                "JgAcAQABvID+pEDoMGznPqRGITQqDPVbrisM0rLVp9GL/4Xz0jbE9pcwQuKtsbGTTurQ9UB7bPPN+D7rc4huJL2dcO5vgHoxEQMFDus0hm2QWF4n6lZasXYYVQAJQIM+5D8qfQ0DJj18ToCSAX2be9bvG1OZmkLPVecw5rEtchCm1yNBC9Pc2byA9rEcQjNsfggb65qZMI30FZLXpGNSf0sflqwt30HHmj5VPnf+VFXIUm7SpXvYG8ti5Mn4kfTRAgwhbXW8TQmN1d8NlzRAvGpnaR2Ciz9zzDbziLhMBS3W2iR2fxUQtiiPF71V45bcxyaoNDM6je0zgMACp8d0yPChdvhr6tULY8s=",
                "JgAcAQABvIDhlmN5xUJsTxP6sFA4fSKHYKB0e7Sh4m/X+siqL7/uP8f6ZAqWr5GpzGW9NSYZP64KeU7pXiTSOUy2/4ONKjqrQ+UWtww2vElpQFyUqlJGh9JKqA2VwZtwEPJxbL/zTJqyW9nXoR8G0Np2/poYtKEydGJlL8QimYTk4WtpI64y7byAuwpRoTxc6LbWoCl6Mz0eaLKMn5JgEuKHn3TJ/Hi62nmhfi9NYluAweMjXYgxaxdNKl5N4IOeL8b0vO/fAVVIfmKKJkq9kAMiRHmOSc4LS15Y1WrTkCSz20wKQMbPFsRzddm9Ml4XD0zCxJi5Bzz2AO1Slo3y2+fkA8CkSjZ3wEs=",
                "JgAcAQABvIDUroGgkg2WRxFJG24Nbfu1L/oAMytKuxWhXZqcHWsSJHqfxHIIYJmq0TOhf+XK6wscZ9WR8dEiH4cNT8vF8KQUIc1nogalYj7CvSQ3J30c02dfN3iEq9KWo+iO+YtbfAaxsMkM3Spv3DrRZVppO8EY8NxMIfnkVde1gKumA+9q5byAzIH6X98srvs+KsrAt1caTn1J0vbtIx5aAbKzZ9G9fL11PDb4JEdp34lCjysApFsKZZ7RDslcshcMePtOtqGjq5lUICB/Hf3MAGseTkSkd6BhPqnKbI4znfNhNqdL+W9eOpuPbvRT/ZdURyZ9IDyD53TYJXDJz/XKFxU/BPxivUU=",
                "JgAcAQABvIDzBzJyVhj20GxQ+iYE82d+YjaITBq87fHT2vqIp+j18S96S2kDD9p6l9+SGym7RU01tkoOfCJHRgME43Wf7HkY8IwDf7A7WPPeH9AiTOEEsvJhmYbEMdyO9UZV2szWHAzJsaDTZ4iXpgScVSs/0iA011zQP0dlC1hQ97hpuQNJmbyA72wT+EyfT3suLerMv6x2yfwLXMTBNSa1HHX+J4IurD5P4PIy0yjU+dJHrsnehpSeBQkX0ho0j8RlV7HZdyXsQY1FVr/QWfqEs8Y3NrAt7jkVvwrUYoTI+BzdXHGwrK5WUtr2K5oxUVLDIKH/ATJq81A/stK1DnrZ3vIWQvh9rFs=",
                "JgAcAQABvIC3Q1Vgk1S4tQRZmS/Vt5PmxcsFUjXYGhADfj+t7XDb7aOg8lZMRKdUZ4A0PX0YsJVN5XzAa1HwYi9JpNRhVQ6S/ihoWMX6/lq2GM1B96yg+5mgocCfa3wHKH9ZRr7qeWSZLip947BW1jfSiF8afKCg1zm7uTKlSnyMHRNsLi6Cw7yAtU2AtRxbGE1f1CzpuHAXGdANIxLF9bSeiCmx2Mr9cNGdELvfnQt6GGHXZNAHWdvol3Dt3l1WCN/mzTbdh+S/MKzE3eI7W3ZbyMrn0BYGeDX6WemWwJsC+CX59sFbM5HN0L9wt79qzfJR5Slet/Uj7s7ptbHNciLgMSH/45FOa7E=",
                "JgAcAQABvID44Qh+6Ci0X7dl4GJ6HYqB0unuwxpDbNawHQK6rwEknhic2RpzMAwn6zgQ+8m7lmbzuEkRKY+rccfjuTVkHyOOjaJTGRGPF0nppYiYdBFaThTR+kzoJA8M7jTVcGO/4+fU3ZiWYENIddgYm/ge1vXmuhYRVfDK/9pbMV9zrs9XAbyA7wO7vhLdwKaLANdyqwzafOq7uP396PJ3v5+3bfRCy6Rk/d5wB/EOh9os/F7SDShajh7pTw/uOQtKwYaNw73bmNccohqpA1EfOvkaFk/MPEQuHt9TVIqxBUn/U0xe7Ago6AHJxvZtTeqhJDAz1V6qikoLyIS/k/Y7z9jcJRb119k=",
                "JgAcAQABvIDzJ/IG1CtjqSTHEs/xpJyAQlyPeoERn4bNVamTscoqqFcZ0/8RJUm28fcIo/gWntAC1bmh/EenRshiVxRvjIq7P+shsHTZ0f6iL3cRxxmQpeauCXJIAD5YBK6EPB9pGQr7AzWUOUJ+aKjEJDzxInLjt0ft9hBq5I9/U39MIimjI7yAt6jP3wF91yTcsiJDFCqZLGIBpmiP9/WM5YnPphYRcbkTA+lk6DGBrAn78PRuPg5eNfVFG9Y816pxvPNz3hdkDs1u1IOCTeg870GyjXl6AY/eSI775nUlQ3d/nYVWX4XklZohHHNsALBDT4l4e5QfTg5R4k0etafVeIKfv4vyXzc=",
                "JgAcAQABvID6mnhYZxMdpV6iUXxF3gFy7U5yRad2Yj9stVDz32Unbj/9sFrH+N5YRtR4f8r3+8lMRgA4kNfRZC4QuIZG3Ezgk2eE0I5jnuOGC714UXhLPHl3s/wJtQSRfX7njmJtdJnvTcKuHXov0HPu1a3nx4Vl2Ar/FrCHXdOdBcz/vV1qi7yA8LEFMI6MenkGpvx0hTB+HqBGHz3h/mgzZc4ux/EcWMDmpKR0wm8nTfu9N7RSIP1mEaQ79nb1PyugQNnoNDmMwZ8fIOo28VYn2xO2JECq9T+Aj9ZZmzhh2FhwzHGMqUDkjqcWogtsvmpmDAOqPW3oN6p/SuEmzxpQQE/s+KoPXL8=",
                "JgAcAQABvIDb6e/pnmF7c9S1t1wD2lcjO1obuHp92GtyTmomjeszHinWfoQpDJ5teziWJbdj7tRTdcTgB7rPpFN3W6DCGtlLQR3RGWCTcKKo9riMQJ8fmbCTFEEZGo2LtGjcIRyzZJ6ecmdFeckhp5WW/XVw3NjftJd6BuBEgZYbDzS9FZNyDbyAvuF41+uxibYOjCpEh3JrZ6m2+DRP/4vJTGpFH1Td44HyidGDK4zMh3Fyagk3bewJAC+4LWmpDrXCACCRnDQDYzsTfKNNxeHGxnV19Bg/ZbGjG9DEnP79yBvrSQH3/bHCtVLiHWyxoqNOgnt0mfoB8QL3G95GoM+/wm22I6p5zPs=",
                "JgAcAQABvIDxrJCawKNipFfn3vJAsb48IRyLdWJ2B5y4ZHv1xjjRH36Ybj7t1KVFj5MFerU5Haxw8X3CFTaj602EK6npmdtRfuhTefJvka+zzlb1mFdIeg5TLze80ufQLxJhY8SMbkNxEx9sOQdldRpvgko6FWZHYF+te7vx5WliUTMSyWVHcbyAtzNbXaUNCTUPJwhP+Llc9gBP6C5CIIOQilovliaK+jT+TE4GVEUfs5GVfzoHojUgnlLbgAbWvMgvgT/m8O0g4SIe+gJ/8MaFqrUq1JMlEI8Z95yNuflcznf8E6nt02js3LN6YJR+iQN2preGAymYfwe70fTNHC1wr8mX/MYzqfc=",
                "JgAcAQABvID9zm2bJHQnMpT/0iPpLQPRdA7PJPFE7qWgyfpYBLVpawMOgdGfzBm1Y3/bG7MO/g1513SY9cEP27hZC+R5c+rUa2vfY+EulwwrLZyDJWQQ3iW7JJLgFaaa+mF5r/u+DuJosJAuABiOeX7GSxrblDv8eSVUaHA65lujX8UpmSNef7yA5womealBcqtp4FKLCK/kH6qLb3Q9iJoVo8o2K32rpLmdZtpihUOiJZGK91QYktxroQcUMmENNXA1XjIvoNu3IJPovrV9EvlwA0/CEU64Wv3bNB9kkrt98uuvTWIe45DjhFJBZKWW58/q7yjrKlHCSN1E5yb3U4ritV4TWgZA0Fs=",
                "JgAcAQABvID0KOpTjYFXr2Cbnb3K8IE5y09TqkxKxfmzUmZyb0CScejkrezKm0YxQp6lRR0a948JAHgxumruk+p77atukjnXF5zw9jsDRkywQfoeD/oc0ewJtCrx2q87d50mQGlRMUAO65p/FjhOcDEH74OmPtqnhf6H/9odCTqhdKAczTlMF7yA4AEnw3KRo2fcygLGD6zZDZqRM9do2A7IBFu0Biuqt9d6oRyPabEydvCD90IoU5Gv6VibqBlhadFG0nC/YF1S3LJAkKCkmvhA54FgKySVvxgBxJmHGAXKbWPk6ktzo/uK1LzsvdkSsd+jkrWZblI5MoypDrikd4Y4GVETNIDHRxc=",
                "JgAcAQABvID4Pd6gfoPh7JjMlkEnDNezmXM+TPxmBYbQqdbh3ZzLubcdknJbjgCisT/oSEJVOKHzQ1mv41BL171gO3toDuzI7Yb/PPuCcgsQP1JM5ymqfns7OjzMLJRJuycYRJU3GL9OSFps0r+02B8kgf9dPNQoYrh6Zxw3k/i0RZ9g8o1eD7yAyXRWbgiNczcsKVsznA8fHgtgZ0oflmZYAHo9lPJGm5dw0+1oH1KCWt320ME0zPGPtsW2OYsd7FoD+YJaUDJvIbusktjb8xaDtwQr/yey5wx4X4oTE2TK7vXIwF8+KbIYoRchbzwTNItHolCqq5/nz0M5qamM9Qb6GcyBbiql6L8=",
                "JgAcAQABvIDb7/VaGstK7pBanWXuYkxqrlaTJ7WJdGhrFgqfTLjXd/YaYgCuWjIfYPE7VWGHaBjed8cI/5OIBpjJ5yucR3lv1pfXrI4G4uY73WkPu/H0zRNJWhd6zMEnEoTkxKe3ouPRkBt3ZuO/OQV8AaPS28MR2NS6nCfMgPMN+lC6RO5Tx7yAxvKAzXyMCyXXY6jUtJ/kQlU5RoK/IPYFlaPg5FKB3g9Z81dHCXVCxg5oHAJ9MC5MufUWb/8cqAILFDrXEYhIokCzeDGQUpC+p3Khbms+EfnGoB7cN5R4ZvO4HfoNIYmkDIPb8YabrKzL+8J5ULtFprjIdJkrskhHcurqQApeBac=",
                "JgAcAQABvIDzwkyQYFKJstjTSLV6PMlaIjNp1ZRYkXMqiPszZfD0RsHyAFTY64P+lqmEYoox+jdrJBIAJjWmn/2r9HGUmCjYyledmbPwAMT4jkiAMJDN5sg0KO4N3Q6DVkamv33nBn6idl1s0Z8NrCXoxfxDMPgMakO1aYZio62nJ3dwjfSqObyA3LVMeJVgxr6JbCKXbt7cBS0UPHzBR9LKxDlwkJAYqChwX1nj8oq4RKWrL+OkI+r0n/vnQ+0tLh7nsPV1v2Y+SC6jFr9KRLjJG40mpbvy+hA9k1sBIIqUZf9JiwgNuA39GODryxpTijZgjjScPKWGbquR4HNcwoMmEDjwi5Y8scM=",
                "JgAcAQABvIDS4gwgqWeke1Hkebf1nwU56yhExUAWK29ENsGEII3tfd/4T3lSCvu2lucsnZrGj5pqM4NgvU8aLLhEkLc90JA4LkOaM8xeBJWo2YzKKrhaDr+hWuIMxxbyLKq+win3+xPKuw71v3AkMkfRiVdgG8E8wlhvEDr3CipB5eWmWO1w9byAvQ+tednwVV+tisWImEBMXVXqRrjsKSFpfzIcUNYINd/wvbmkkQjPnhZ+QUsAuFNeyWgvMT6Ev3sY458T+0rXVut5RwuZ7o3wPZa9iT5tPL768EuX5LAEbw6gL6i/fLBMuvILtCFvrPFDqxxAb/7huEEpqXcjDzeQJwz/yBZcOUE=",
                "JgAcAQABvIDMzo+DSOhOuJx0x7DpWcscFxEIJKJIyJSZUNj/U5L5+GPO4SpAeduLXsFj8vNUqFpiBIkSumjGDfhRC3/tDkbJguqP5pxer8vL37JoE5O4car4wMo+exbj5PI36JmdavVSWidgA4VBzdwNIGxA8VLr96EstjWlPJUwuL6DpL1eG7yAyjzfRT805DTyQBsJN15vVfvrmKdNi5O9VTTbJ8Hj7FiGOP6TrnPka7a5ctNipRVFevT4szzVe1+4xeHGJYOMtMb9g1d7ky3hUK2V+Q1kxPzru0mJ5iF/wMGoZUWJ+1heIL1tzam3i9oxzeBBGa4jjhPc5AQ8N7xJyC65h+HTNqs=",
                "JgAcAQABvID9CmhmTBgQu8nFGVWfIBunoNJGiAChHOfKEWxlO7rz5DFoK/3s6WdHmAUwAyzwQKehlXwVftOwxPZ38Oy/6WJewafhcI+G7rw5CqLosbFep5iNErZ8ixF0l30PFOEDOzLxdNlexfQT+IoUPa1S88u4Y4243pC/aJpj6G7JZ3Pko7yA17n80EiQLinwJSs9DqMuqT6DsfPq943jCOZWZdyUGRxIXN81fDjT2B62hrJbsCMikBS6BuYo2BrhlIwuXr5yrhLptSO8yufCODfJ/4SfmAGqv/WkAvS+/txH3N4G1Wdjhq+8dS4ZN9835ZF0Klo6bas1GIDvOkHC9QOOM2fc2oE=",
                "JgAcAQABvIDUTVIGxILuX1lgndS2bEIXzWf4xqtsD2l3bMg7b0G+gpyUXHWfe/Bj4iM3haZ1ninciWvwDpK9Psfy+aIysSA0vPOcaouFNh/YXp6RVyryrf1zzY4tAyAyB7k5BjtQL7HAzVu7Ixu99hj0NGQ7R7xlM2wCfgJmQDdu3VmBAlYej7yAxhxix/ikz1B2OPLNkDrHkSL7jOAMf3h7TsmPu1UVAti5ADpdXz6o4EZHT63ukha1VV6Fk4SAXB5EiL/L6M01nrGNT99+8FBmr9yYzsOATT8razj0iJJ9CTqXJbb2ZuYcHp0g+kZ69r0VPmY7mOUKKMJY0b9WkN/JaiK32KZuzHs=",
                "JgAcAQABvIDKqRYM3m61FTjz4tE9wMhn9s2Q0nYZdg66VlYuNrZLgKIq3dJikcWF1avkEURny2cuk7npcMh560nmFXbjqCqVmbepN/o1dbp78ThuZlfr4/xCx59iGLRLj5kF4GQ7gfpgB6YknkgfxcnrsHOrn0t6wdl3qEkGvuE9exI0wYwcnbyAtbT1bJrjNRorCCEq4SYO817bPyyX8mwZ0GA1K7eoHsP0iBwPOGz9oLJc166Qs+e+bUX19A9aGs/XxhYtIDtuB1gXqtPxGgmc4cB96okekmBFqLdYhysCoEO7jesJsAoPhXLPRMoO0FkIUtFHHRsI1yZoNeRTRMebc59z0YLd/Ks=",
                "JgAcAQABvIDmIpXUVXqcOgJyZsA4qemM3pjg57vWoDqWdJCLl+nBFuVVna8WznMN944+zrAdgr88oCBYyQ9SQuC8d9MeeoMAzZyOSdKsvdd/x2pYHVJcJBOMBarE1esDDdmIvQc6h4JnILNaFB6GJ5uCY7vE0z5kEg9swgwigakM2gHDLmQABbyAy/c8U0lK8nW2S+YEuTL7wbwVhj6Y9EVzfGjfjGzvgd6pSCQFr2NDXCN+J7/W3RNzxzn5jkln/K0djpktJOChfsEYaSDaQIvkASvaDRkn/1M28ehl8/6eJc5yAJSvx4HqeVjGh41UcNElM0HRtMBou4vmHWhdlJHyoi48NHzkwQ0=",
                "JgAcAQABvIDhboxW5XwuEthyG5ucX6DhJJHdtatvxt2UhO2cVdUUEBUz7bg5p3HP75R8s3rV1PDQex1pIc7mg6Dtc5vNaJ2A1SyA9Tz27ERfgr7b2GBiLJzkOQtol6yU8/zK8Orfj3+qj+cBlkD5GqPPewxzUN/VtbhRRTM14ikZj8ptHxbv+byA3a6WVFu0ECVJZjQsl3Kh7LhBPriQZ7esNDo0ZAHt384Dja2yryEYZ/7TcIPq8SckRh+vt/PbyPhMHWc7bS8NByaCzSu8fc3RuHuRyIe87kkhtHg508E11Amq07jJEiyHfrBNJBUu4lLh9CPGIbUSdH+sDR84r8W2B6WcxNyZsE8=",
                "JgAcAQABvIDyurPfFbHQbTv+MhvHMhQVFSHWjriITGArH/cFIe3nWch2d3j7iQnj1mZLb83hrGiqJC2se39iUdaUBdhNVuS/h2+BSe54eImRXmWuNC4f8mkT199DcgNYNxs7P7DtQ18qSEQuW4ZRzCVPPGXsZVQK0uOqQMPDR/cnBAeNeZW0PbyA6nkQr0/0VddQPZXuKmk5ZmPBrY/b+G5UXzocJNdyS7bbRs/tLffFQXEjlWNhUDIt3PTL9nIO5cahELotitH/9Zi5CXm5Sjk4jQzzc9h2JiTmDPBkdQqWskxdtX8MyVJ/MQs+bhIvbJFXWO6XlYp7UvJYMninvm4YMRtWzJgZG/U=",
                "JgAcAQABvIDrjfCRqXEvYbuCUqB+Yfkm51oO3YOlav0taPWLT4nl/x5gkqRbRmbrMzyLDiLlBAiVebD9LE+J21rWmPs4FK8St1nQe2ovM3bMLDVtqwrvzbWbe4ernhAmq+tURfBXyisUHgLjUvsDoZSdvRNwkLIM4nFFeGB7i44TC1xh4Uju67yA6AS6LrVAdwtd3GSd44uVHnn3hI6hJ/lL7AKQvEeF242I2+SHUWNFCST90T/9dW/4gLWpwq5Mqj7rXEdkMG/oI93nMmID+zmUIIxyxStoDK4R8l7IwdkRfdFSn1jQETp7UbxBJ6QJMn2T4MIW+ALXymbGrwtg37Hkx47hZMjbKx0=",
                "JgAcAQABvID4BOQs2rZDysW+4WILmjGYVVcz88edVSdElYQwfxYe0ELRZe7JyOQ+hC8ULvAJZWqzP8osW7DQMg0ELogBs4RPSE3MNySRpNPWReh8JPD2qlJE6PKl6mpe+aiwDJ9vGM3M8MGJ+dMLJ9dQyL4y2vx6ia6CFHarZ06t9eLCLfMwM7yA8lbY7JJLPjt5NPl3gOwoisRpX4iCk+P3VrmW0zE1s05CihVYHyctzfgCZ9f43EwlzIvFaWpT87RamD/s/frjQXteNjO1gssio7uSEpAp8Eyd8bGdVXsrt9bVgLmwbnIU8kCbQGsAYs/R0XEY8P5crVxTV++5Hrmsk3Cw3Dk9zPs=",
                "JgAcAQABvIDdwSas+ff9g3Gy7KWioGBYBMOqgbOF6FMp2sUeQ03RwbzCdhpZueU18ETmNwOr24BLRacKFe1+agzrtkazA2i74QZVavtAhH7rQZD4q3yL1MMGv3gNtA1QNnxjK6G3r3R/HaMlVKojUGuVoyn7zyMcMEqBVP8/kDszRkFZamxvZbyAvrQyvA+jJfXcAI4N/0pOUHs2VIm+Mkj3SCJrfLDk8vudgqhET5ntw5CBKKZegXx6w8ARFvVSAGWoUnNKnshKCGUX4PjQhoEctS+k7+BIkjA2nj8UiHDHRqWDQBTh1AhKLJ2OjLUJPIHBbypq7V98ypXjDnJ6cPW6l5uCPxh6FbM=",
                "JgAcAQABvIDuBomelZL6uMiWZ3R1qXfMBvnPskcU2EXV9pDW0z/G1dxN6xtXXpE9VzzE+7nALmqFSNJLXqbFDAEKHtPwS43h74mIDDVmulXrI5/NFqdEtaQbSTaWxtNq4E7TlwCf+dusBde7kUVrFWm1cOux8ztF77jiHtUIKpGECERe6GQm0byAzU4gYanM3cgMTKrkV5vp+PcnB7bXhTtgWUye0Xxgx/o2fELJrCsI+iIJxtUZhK22oo/z/vA1V4QXRm9pH1lPyhXFD/cZC5z5SE39o+xBvNFi8e6kQxHo75pFi0zLqfJbVTui5c8gAWS32C/QzsUd4MwnqUDMJI3Zp7gXvs6+zdc=",
                "JgAcAQABvIDqOng3QCmLUj7S+2QCBbYdAZahMggJ0kPb6eqnoZRvi9KjkLHIWw0jmYyhDe3zGqryhZlpUitiggMPuKOkwGbtAMQ04H6uWAHBlKC4M+ramkLqXs7wDtYc/EFx3CrPOGvddAqZWRJWX7q5j2U+fRi9silHBeWcjxoRXQi6UovRBbyA17xtvf2WFHnk0J/I14RRIKepV64EN8B1b3mYoMU3xdLBtf54tDAqH7tbgmQDbjV7RVodRQdF8B1LNrroYe8d8LQdgHPQvTuKYUBQRpSBJ3Q31AjYcmI4X0OaTDaeYRhSL7eftmimWfSuA8K/LDRZkSkoMxiYiJ+4WBcjeeK1SLM=",
                "JgAcAQABvIDaBUhdtogj8ObPQoWQXMu1ZXLEHK65HyDJl5SEyyzxW+f+mEhMiy3mJEcXJi6umP2UaDowN7gsYLdJD0FToKlIQSlK5I6F2BcYKikJd5o6ZYUXa7dMWWgOHVWdtHukF5AtiJHeSEaHkwkKdj6Bps50cdcuWRL7r7AeTVvQzdzxn7yA2V5TpHjErpHWk0WyrkjJ0IPD/z6cdHVrPgYNZRAfkPu4oUziX4YA/gR1f6xzSF1djoMdwRDDv998Gk6JvpiirI0ugQSBie3gU+eiGo/NZFSRaXwasA9q08q+ertqDCT/dsvYI7YZx9oVp5/BNrr8qNUsXKmiVezb9YcLsb9fg9U=",
                "JgAcAQABvIDznzPwgciQMl5mJv+b897s8cmIHwTYChhaa+RqWTkTiSwegYZi36+6nFEMW5ZWaQmnM9BVXAX5A6StsaIAeMca1nyBP2pm1vP9GXX1HmmblIESdbn/d2LvFZ1pBp2bYRkPxDmKwI+ZslwKjZV8eSr4DA2cLhz92lXDjUOa/T4Ab7yAusFTd0KKRzkSf2WokhKGRaZVYaR9yuk4l4745wvt+QkvbXGH5ch6SXHtYsA4RatrazrcxLTr2wjXyJTpr+LUkxS5UcDnDGUEmVN9rtDX0SOAznKTmLmvlcVvXXjpfL8DoIGHV2itsS3moDHVKHW4WMU8ObsdwvUaqLHdG5kwTf8=",
                "JgAcAQABvID3gVdJX/fR1833fT+eWGhZGbWvGBWeEGOF6NYiFPy4IJE0k8/yaL22t+iT9tu6edU4fgWkPzOJDBiYJHHcC8E3sDLeWJFLrI3rVEeMHsgT+KUfwQEOzEL9qQxzIliR2rYMxuaO7KsgZpspE3qf2Xn88BQtMghTyEwiThZJPp6TFbyA88O7nFoKn5e0FpmFK17tLuz0Swg9h83Qr1xnM2xjneo2vxkPQKrxxZzHMBDFjs9+Uo+2t1Ka8+w/dpgcLhWOvt980INuZ+X/SpC+UbaDF3xD7BML35cSmkwXwByw3W1CWlt+CPgAoQCZvRLXInudKSaeZYLe7b6Dng0I8rZD0dk=",
                "JgAcAQABvIDJ1x+Wla4dFnGcSrGk+Ap1p0uPHxagP87Bv0OCpxOUu+sXXndnYHnFugWXHcwMuw+++x4M8elDjxQth3PgSCcadAMJiiL8rEUJqFuJ5WEnuXef8Opxyv/a7DvWaoMuRDgxOGNM7FLrXh452IzYdQ9y5/ZlvI7938sqPlOjwj3qM7yAt3JbI5mVGtxlp5oCUGDvVxiP5SWUK8Ey3Un9AsJ8ORlPhtsL1mdK+ocXqypXtiN9247IAG0m1a0c1MtsgNnZs44DcZAMa08jlIvrGNhbUeYZ3y1sqWqcAmYDDa5p03MYqH0MFgkLMIhOCHLIu93zY1f1xClhO4F59NPqkuEw8YE=",
                "JgAcAQABvIDyrOa4Cm/aFptafzQpzXsmFMqPB8RegCa5mG+THo6vOQLbEQaqyF8F9WN75MZtypd6vQlD8j976KLxkPPFI6PeMgqvAAUpFCgILWU684okpVaAfM30JiBvHw6apzifF1bZCByZskcrQoa5ZU92mHVfJWKHrqi2Y2aWkKVkmkZT9byA6u9012I9d/FFbgqxrxEu4eaExV+hIAY4OxGODsZ5JamRn4TK9A6rz7mOZHmWpcdI7iGLlv34VatN/KoJIYbIKqao8qz7ZW1vfuinXVbJQbTNabJs6Q9jBwOK5vrm2nzNRGOU7dDJuFfyCe73ySvKGNm9vFRjHm64lCaxu2EADCU=",


                "JgAcAQABvID6DdX3rMbHoAsbWJfoYGABH4VW1+WZ3E7YEjY/1ffmOhR7jtDYosRxr9l5sqmxs+T5R266dpA3+cvaLffdAAv9AvQjcGvV4aP6vskbEyG5UmrvG73a/CMcuB2FdEninKUXjNUbcQJ9JnTYT9Gkl/NU9cDS396o7fVbVXqOREOQ87yA9P6HA6X5geJZirlCZw6Kc+htHN4i1Hf4fPl+S7gd4zlTO4D9yqqrqyNcnWBjVKnBaztaS71b7QGOkasUVluQmHu0NW6Iv/aubvCFK2LXEVjNp3cQYvTZCe82A9pWY6Ud/RbJ+QvW1tjYRJUuUlAhjFa3HGZRbW1NRvJ5FD2fy1k=",
                "JgAcAQABvIDuqAYV67YXoyk1x3dTjI4W+NaA02tSOd+Yt/PwsOWRgs4vD2sJThAXkwl3lMhkUsDiPlYGDkhQYSyM6OMtXFm04AmVxCrQUmUcLg0szdSrah0zfKqd8Vqzf34PMZF3IyIWnRISUdNs5uKMupP4ufI4k2RtTai8C5ubVM+oA7b9BbyA2vJSK07j2RsRNQS2k9LL662t1L8C7ZXOz5r/umB4VC5B1W2Nw+qc45NcpQbPAtbkBjMx9entO3Jmv99EdV1modMVWMPlMLFuBMUBo5gYVrvL8U/1qC4Hdjay9J4k7jbQ5txr2c9y6E9fgwQF2ldfViff0AVu0NBbte9AxcwqSbk=",
                "JgAcAQABvIDlj3vqzSE+Q8ThSg17nvLyq/ishzY2F26Yil0dwX9wLHubgJEkHQPxqNFHx+i6P8PIVV0ffwy8JNljd8gKzAthoKFDtyXQV6Gf8tmn+hjBkFgdwjNCI9iK7xfXh9JI6+wqoXe55WTtA1ozLNaW08FhwcXhPbw73IQx28C6OYhWQ7yAz3a+IuUi6OEl6e8SfgDEq4JM1iOH+wj1Qp2bL1VgSX1Q2vYCTeHAwxCyf6+wgqVS5BIwEit5Qd6X+0UtYWwuhZNjengGZAgA/vgsXsbzSkSFYkX2mwlFi6P7IRia5vMt4m+bKAdjMhIl9KgyErs07BdnfdnDXMwS3/31jYmmDAk=",
                "JgAcAQABvIDYswGu+41pafGD/pOuRHXU2GfurKb04Iaz7/rIWax38PZ/Jsko1XGnDN5mUA/EkhwFSDdpHp0tGgpYjdk1d0nV572vy81HRLoyPSpxCho7J6xjDf5e5+nXxNzXtNvZDtKf/c3KPf6CzKPJFCKFXcJJVuqoCX/mUJ3RlIZ/a9iD27yA2IsDU0JWf68oJ+Ec3SiCAOAopimVkPk4up13uuweq4d+UukjtNtvVzMHxfsqB89kGbF8BRpPHHP83rCETcadfgNSZJfFoBssJ3indfsg5yMvl275q6hL11LOlbucdonGR8mrlgIOgKF+9F+XkqEvhEV2jw88aCB9fJblrsccJm0=",
                "JgAcAQABvIDZwUK1fxy/TOxAQxV+NnGJ3QThgF4t3JCdcGMzRKfxL58yx+6RMe33QYukkPmL4NsRWPvQgU4Ftz3THfMnvKmVCMACtrgp6jJX2QSMC7duvMZrU3BMMpacsLlFyK4Z47YG5+wohLsRCWs+5KZKj0dLxQip0q5uhbMbckI96FgxzbyA0Uagg053MWL9pU5B16rFeTVlK5QoDnAOposViMxnx6dkbeWO7Qf9yLjCEB9uFEJhjU+vCzEwKW6rI26Xpj52MZsp9f96lPowkbWT5yE41tOrh4+J4nNSeRn5KAIiUMoi4i+pUsVp9hdBMaZB5d6qY3+tmUQookNzDzVIf4SOsRs=",
                "JgAcAQABvIDn78vIjCqdUgos7lc1rYIvVIu9wjvDHfZEJ65hMGUTrIFo0ShyN2lHyBKWKQbw+WK/1uALez85yGqSp59pP05WZ6qEDyaUGEG9q1uC0hhroSPaZM4J2OG/XeAfP3bZtjaUVaIY798JIsYyVgNVSrfL/QUVFFGdQjAWwdz85zCQY7yAuzcwqKQJ6PBFRB/f9p2rnpDAC26Xyl3oWMTm/vKZ+fflpUwQbAahvO2x6wgmu2omjcv1r4Ny6aFJFszwk0+bzpP0w0Ec5Liw93dBaYwpGXmO1ss5ria+uWGeR3YE7rhRT617KB6BJo8OHADEf8KGRYLz/b/25wDYelPNuquBBm0=",
                "JgAcAQABvID74Pog9Za3/uknEv2fNJxZAUUFE+MW+RjYJRnTu9+UxP25u4HD4Z4uCbzZnMxaIBWp3QfuRSB0/mEKX6eG3maMsY6c/BLsjQutBaL+LXgPLR3HD6BoxTCaaZZQfWfh3SaRCtqkrNPWSOewnF8ADl0oSoU5EWNCx7nwrAbJCrVpI7yAuNOqJ9QkSE9N2UrLaUbb3Vz3BJshUCKclaNaJCkOof5asvA34MavYsiYJqp6jbuTUpCpSserhxZS+i9ZwXHGOu/hJUdgcEycEYeN/zVThEdLSM/d0twYo6SazbiSxp1YD+7zs1kZ9lkh1IqnONxqanijGFuhSRnlOuZhbJgbG9M=",
                "JgAcAQABvIDlwbTYYra/dKy9Iet4OpaOuJqYO1F6jQvRL+JsLNruWiZqa9KfyNfhUcgRxQqh6P5jrYk7PN48V+q5NuZyQHp8nLmGO6reywM13EH8g2toLBK9Pg9zhgp78fYj/8lZGS/mazQ9jCs2cGOvfj5mvUWKTrEP7T5GJZRbK2CubMqME7yAxEglkxOf910eSnpm/TNS3KZ5nggDtcbS1VkU6bvsYHvJ2JwelNimOiWP2i4lTKjtGjWyUOLlGv330TUtU9gk1HhSVEFyhPHe7bHXlQUl+lHDuEvh2T/tRR5fSRw4FiFGyaQjNUGO3543/mYRtrTULp3V+D+4rteGrwwsU+T6bPU=",
                "JgAcAQABvIDxTXnzWR4kJgzNgyDt5Qj5seMjzJXDpMzjDzRzbCswAB5RiqG9AwRvvKmxnDH+taoXAZ2S5b6YbfxmvVBkN8HCaXIEiANyVAR5icD7w/09MtTQ4/muBUvvk/Gv07DpBxBB7+tmbeThzZ65h5HKxCCm+rEicWIQZrA91y1soB8Ex7yAuOgacVXshROfPoRARk53v6bqumJT1wmA2ya2tDZjFg14ijEyaPcYrhMxbQ1o9BhoUWxt4reCn15UiN5c911BJV/Gt5RgxA1DbP5bSKJxxkhtoedme7vxVFQh7nXYztvVS9LnN22UKrShHIsqzCwx01hPMZ0vkijM0H52wiEfVO8=",
                "JgAcAQABvID0qokMhTjqU7LNcIKqsnDNFleJpUKmGiAjtvQ+DoWqHdRaRiscwXxC8lqhh81V86PFxQRdArQT6OVmbg9/JHPt44Sfzm4fahd7EqKecNVkLTxA071iQsnqCrfvvsjqhVBLg8gwShbEA8v3GjnYdpbLXE3176N1Vo6lLdBEYj0n87yAyDAEtFrmIj5SqmgWZ5knzoGZkgaDQ5WTm+zegHuYHFJGnMh1vBI0BDROYNX39mpb6EwiZJSo9mf87Zv70zV2xWt+kiqGMe1UZP27J/aZqwkX47bwNnREBccFqyVa6PKnHE+UNSGFO86spV+A5O1SbTZI8E0tJt3gb7hop+kOH1U=",
                "JgAcAQABvIDOm89wbEtTmFB+2Lz9q8moAb7fsgFO6+ATm/P1ZdIfe2/HdXM0PQ8hSk667UJb5obHBP198pvzU/6c7pLQ3qRg/9JQQvPLNXaJV+yJtQpX6MiALny+c9YkWdEhbAvwmjS2XrSYP6wIIYnUJ5nEiygiXA1tEC9U73V0FN5Crv2hQbyAyE+gT/7gSpUcqUciQ6lTHDjSLPjT9j15gRCoV8Lwii1nYxDuqwGPdOLVoXfX47sdOV0ZEjmurierf/YK/iA6YsooCxxl0uTi3CEGvyqjWS8gJ0d6YS4Eni2xudoYCA1qwneioaRQTGAXYxfJ2mn8u+eWqlFJNfxGBpW6dS5lr9M=",
                "JgAcAQABvIDmAeNEB5semLi7rPoBuBxibpSz+nDGVQaaesWebspjzV2C552EBEQSBIB2FSzJ/aBjJO+tGfEdddf+UJ8+OIUxqMqhBgFMyywQPFQzIQRr3A8grxy2OnuvzFcuXslwHot9516mxAuJIkq5RpYl56Dhnfg1661VDO9nLrhIugEeNbyAvR7f6ouOUqQ+WgoSYATbKzHVxlljpD/M/I9160f/Ppolt3a4u4J7KaPBijyIi+hj400LTJqXNkOmkdzF85f/MaOWosBrdP+RwSe5IqH6w55EvfO4sjgu1NpupM6MAzkAINLUbQD/6ISQ77gANJaqltq2xGZHxX7C66m/SjevD58=",
                "JgAcAQABvIDSVPPS985yCUPs/BJJUa7pXfxVGhEQnjPRlHuuvisCxB/Kc9RXt11f63J0kMGAxljCUfJ/tagncPf9+ZlgNjztnNFY8KfYeBGMJcqLFH4ZTxd8KlTtMwGcttS7zmno8qsEaNJBgMFMgQNWsHCAe9FbcLCNWvowueBh8YLpUxudpbyAxC4k50mEdhps+fg4vZo6acIn38jkh/SbL6HsG2nJJHJGMTDKOHz8djSvmtcMnvCQLSwMEaQ3KSkiDdljDXAEqRvy/3sjS0Dk+c2r08JerGFaVwDFG5gkK48GN7I+lZPbk5AfCIStqhtL8Ky1zIBBzMcegmKPWOQ3SODoyzxUXPU=",
                "JgAcAQABvID532+32PWs4ZGl+CT5LIg9ipdZuwZpZWvBgUPTTA9UUqKkk4WPrHO1dvE6ALPVjkdf8igjy23MVINBWD/pc7Zl5FygAt8DUFkPGjdoKSkN7Ntn6jQJ3upwUv4BzlVKNEYLghBlYlyImVDuTVMOlUCnxJLCzOCpoC+wE5deDySEpbyA6QM7G+cnLS7WgPso8XI8T2A/ApWXOGxLfCbSXDm5pwV/0+hpRIFNlV+AFsshAl40p3UjE/7NcFgmFndH1Rlv5xkyamscG/n0giPlIgihQehv3+lHdi+29XaIp99Lq8sVUe/AnmaMRP6XFkpJnjy3eldjtj9JgX6D9PbaAPj3pQs=",
                "JgAcAQABvIDzsTXaeolN90p9r00zNSCmzDzgLJinzUOXs81Sy9/+m+9wB1vABxFf2NhG4cTEzoCp7wKBKeDSg/BYwG21PuN/2s42DKO7kJE+69PM197XyF053eAcFvHb2/FH06TT3NEQuFyehi72I3wAazTYTBoLtgNNz34ceOeNZo3Dl7j1u7yAxz2Nie0XmyFQO0riXv31GYg6MfKYKRXCWD3Mcyg0eIa6Nc9amKsomRVOZiL4o0O0C+46SQ1GTOBs9pkfUb/pRTF+ob4hsMe48S3fOgCYddS+TCAtBRehYkAeRxqNQVUx3N7z8/dFAqVD2F1DoEzUeaRzHK9XPQgIIWpTOhEYAck=",
                "JgAcAQABvIDyDo9XvRdSOp9nW6YNOS4XTG5D1lIVNb1yOKVfuZMazmLXzPgtd9DxTD2Yzf7HkErsCwHTK7th/eGFfnvAXqJbuO0KUPsfCQkJAv/cMdKZOa2HRbOyitnlPwdGpi9X1GEQVvne2Ss5j9cYy9DyYR8FwZZ1YizCb7AHyMkq839vP7yAzfYbSRnLD9hSxgshtr0OsRWwgL0g1IaCwb5J1b41obhTfdYs20TRS8LFvwQ8ciTn6vhudWL8yWa7CaPJ+mVmnXsJ3UXCR4mNDyRV5uY8AoYeNyx1ZMBKBj1F9zXdCOo/oeQWeeC/vTcwmP9CHdjTJ51D0e2MGZxGmzE6q2xBRGc=",
                "JgAcAQABvIDHwh3h4WrGfU8DMa5ED78wQyQv+zSpSgO3SLJffJ+9IXOmTCcBOeQ7cSVbp4BGOZ+9erYkisofjhZ5egv0To3dq8nD3VrAdWNcym7c4pg7kV5UAPQJxs803aaiTHt+Hsqo4ErX+ZnaZvXmouRytQmeqAvVR2xG8DnMqff4uHzUMbyAtxDSEA0M5lgMWZOAeHi6DOTa/powAwETpNqB//oO0zfCMHo3YkdOUYF2aO1j/RARZtPJlQuSJl7s/+b1FGZG1SUdMe3fRe1vvLP9BtObOpyd075uHihYFu+O5bMa449ZueCZRqZHTJJgA3fTIGQzAlA+FwQqKxP40GsCPW14rIk=",
                "JgAcAQABvID+X70213ql4FmLxjwra/nqbR39vZrWXZ0WR3vj/b4i7riE8jUwseBDFS6F9fJGoBBuf6xNzW5GmQp10JcLTL7jA2RbhUECWskxFFBWX+qlfq4k8tVbH5aOXmkYeNUnXjbXjPzA8m4Bf+A7sq3oXwKZNJYP7rM0Fk48SUG1TYDYFbyA+t1YBuypeswKP0dKxlLGTubpcP5kVqXLJ4iGDFbU4jPv/UxBS7MtYfoGxLhAiypYil7i7Iq+uyurUdbFAh2r8VTiCQX7Cu36eQHmzqot79BIO+tfO3guxt2T1fjbNXl2cQ5kCj5ldzqM03OxgruXVSBOTSEmxv2U5H9pgjbXwzU=",
                "JgAcAQABvIDUnx8WMKqiO/7ah9fm83F8rXy/lGDKrxIYpw1nK8qrw/c7ASci8qFKLo1tsQvNxxJaV9z5bdK28+uDJTYf2knVhSxQK2KKH0nmUXGr8VXs7NvCHCuJVkAf8Z4I9/4n1DGIRWUgm6IkLhh6ptfNdCarnmSdZmPl4ypljKPzLLaQj7yAx2QIqShmnJhgR6qybQQLeXieNbt8I2EcUd14P0yqpwH8Aap70i/M4GvcxnX6F8a/URRI6MDYvl4ENRMj4GQzNCP3DSW2OcGViSEk72TR6XhK5vhxgUgsO0gvKsRsWp+hHjLlS7x01Apf3S8lDeZcT3WR7KiJDWtmg6BRz8DNcnc=",
                "JgAcAQABvIDOEfiIGrHo5hCsrQ4/6PTIbRyE6NYP0g0m09S7SQDbpeIkU2IQr86CXtBCNbJTWO4+jU2jKbm7SlrHvkFcQcY7ZgJHOUlI38zZkjMB6Y/8sVYcMCAejldTWKEo2+QRkmrRTe3sG0l+9pspscsnPkERZq6cpWXYLRpP4CYTZr4bi7yAuTBCSa4tUx48jDLhiMSFFE+5cpwnXT3yl1d19EVi6K+lXsPA+TUEnqxqhe6BhRJbji17EsXkfZU7aXFZ+CcDpurbx1NdIoPn4JoPq74Y+YnI4PtrT+uF8gsa9K38lQc/gqdJbPqe0vPGe98YHR0T8R2Ocsu62ibemuVUN5DGMLc=",
                "JgAcAQABvIDKDC27xGwU/GaHBbuqTphQoJHDoRta8UOAkaNzmEri2qMxq4lnpW8EWrKwBO7MReeniwltZaOxQsIiBXRzaVol2da3N06Twlt3MZfPJQ6LZUooc0X/XprW/WWsqXLnWef6LGzuGVYS8ADDJiCAByGVN27seCkGj9Ls7zVvfXAz17yAxGqyYc7TVEWJ4UgJv6VdyqSEQ0PFmSgvVdIHH91BeCJfUHeUcjyZvzEnRu5hAcuH/M3d1Nl+o5bal70UBQmJO/6VMPd4bXs7iBwpqTyBR5MzRu+wwLUJPPkSn30UuaNqwv9iBHgfcY+xh/VjRikylu8AwJL/fEjCP7c2wmBcjIk=",
                "JgAcAQABvIDjA5r4QIT1TYr93Dbo7nP8ttA4fHDXbz3B9W+854Sbct7u2zbx+IgHcrS4K1GFHQrUMMadUBU7aPnR8Gcqlw+Krk++ZL7trLzofEoUPW3Oxoezis4BbKGtFPOXTr2MUa9WI8XKRlUiY8Ng3CpoHA2AAByk6Haow8AHfj9dIyjQt7yA0Q+clUPK6vfK8+t0BuBhd57qbiNFLdU5vnHg0r3Ry80GUTWzUrGG+MXWUjSTUiLpXH8Oi32YUQKSXw4usE5rWT0qncUavEuqlJ7nBe4rj2jN/0VsIIcEMpaT4ton/GbZQhRt0NK0m+BJNoN37Gqdkuwg9xHMhhAuSWtUKER9DK0=",
                "JgAcAQABvIDxM+fjjHCbkVOUanciblwnHR0E37wJ4Nj8xrB0Geu+HSunAmWUIIVxIxRnTNmW0kt7B9SmuZyebbzs6OeufigITq9WFyMx511KSPy9otz1U97ilIF2h9fzYpSfA/SYAZWpnGS4nSSEdCvSrmSDorY29jWrjoBO6oK1ZBitefbIpbyA7GROIL46R5jcID9wp8x3qlwBvp9PgHd/kIiDRjtjhziMEqJuwoiX7YGYsycIISjkQgDq91Rh6ULU0jFwqgvmxYdiC3Y5JwgmD5Awvms+Cy6ksAXJ61bkBiYAgcpy24Rszv8vywu27DCVgRW4R5E3r2FsUL7/07PTXuTXdzQu+rU=",
                "JgAcAQABvID3U2C2F3RHUeVSefhH+O32wJv172Xh/W6hJBrpkNS54REx1L/l77/K5DV4vGG+sc9AHrKeK/dhiXe8nbhosNiGGgradGc+/E9HVX2xvuGHPUXw1mAC+S8cIpMIJ8YmnqYMxeUd3SisLyM6idQPNrSKiwGhfbrWQK7AhXfIGYn+6byA8NBDRgoo35dNI5/qMfKxlv5yNGzZQtx3QDxDmrldgAW9leQnm/nDxs58xfBQ0BDDHBGgpY3sOJ2qge0wl8dJVWNu/zK0yatwrUfqIjPnMHg71KWNp/41sROSy2FIwYaXKUoleJO7l78/ZYu72VgbBg4km5JUYLg/sdsQUHLG2L0=",
                "JgAcAQABvIDvalC38E7HaYW8qVIjpfUPf/DUmEAwya+RSiTxGBoUfkpZoL1gmQHV+uVL3zlHeH5n5X60Y7amwuoHPW4gy4MZ7qPGhU1midsQGr2E6WIupKhHwsq2D/5gI6T5CUTs3bkRVblmiuHwDs3q05Rc6tkFgU+6nOOet+oI+MEBFFoCk7yAtTke651QO6U6mI6OZSjynw5EG+yjJEgcr2xp0X25XaUYSO7bYMR3m4pKPpkA2TmV+DuDl9jlV49LfobtskSjiuAXiRvJvF0ZHo49ix2ldniSYGiTc0hmecvMmXPS2lEGJlbWMF85VmDyjAM7K6bB+KHX/pYInPcdNbGsayRx2rs=",
                "JgAcAQABvID5a9i0TC83QjjjAKwHb8I3+YQfUUt3x7TsIKxwy5l9bZHy4epm2AR2xnAwHuUe+vgHT0vfrXnX9hZJxvJvkg3QttTYXcQ+oTyapvTblnukY8cMKjHV/JZoO2BpkYmz71fXIJRJPHK6TM384x4pAekwyuzQIaBhqZ8BqAGXVMTgPbyAtqVumririATjbq6kHFJWf7ylzKncs702x/GKppUwZAKgfx6qUze6F0T2eAT7shRYR4aesqfNrmtWbnpDxj8qhd8qx27LVI9wOK/zNh7bNdnbhQ8vzLf6eflUqODzsJ3TKdgI4hIKz8O/i62jBQ0IWV++folLKm8Q7Q1N9RUFwMU=",
                "JgAcAQABvID1c9IU9PAS9UfTIOPlughMr1MOSdSa9yJL14kebr0KnEV2OZvk4DKMQehoppUlWjmY1edjFmO/7rPSOhnkeS4f/lC/6HqiKr6X8h0QTfKKaanOAueeM6L3yy2gYJJN74GCnNoH+dEcFoPI3pUMAMjpFPPKFfzHI1cXNzK4Z2F5qbyAzEVBboVO7RCn0FpCq3mfLEgMEs3loKVan0Tyb2stfq5pZDkLLv0bIPu+cBsDxs7oqvP6C0JSx9elWKuTKhxVEOuFCqJfkxSjHfekY9UyhCql0NKsJJs1vlJNTd7/sw3B3oUA0DT/QoIq85nkOpYRS61i+ncZPhQ7FFi17wsqqXs=",
                "JgAcAQABvIDPGhXRFuhLt90GT5thmAfFxhpZUUu1gP+uIv260W5OdX6c1U8XQ3l8qxiKZufU1qwbFtYwmJ7ynOT6YZEnMi3fWl7lL+tVlSMvXveERZn+BZYnHf1vN90Pi3pAKucOUnwzUBb8DpqGVDmh5/s8TsgshiWFilpdAmzh7xnzv5oNA7yAvFa32xKnV1x7EBbtlxAvkBpH3IZ6XQsdl4vxDgq5HzVKZcjlBVqq9BFTM1FVQ45wtk6c6JxTdOkV9Gh7gPTXl2t9H+5rx1gHl+r0FcMNIZJj4sG63wtjp0mNuHawuBOaaxNA5UbYpP05ZPj23wLqtOyqD5gXbp15ufZBIGpKkrs=",
                "JgAcAQABvIDgr2Op8Mh7LTSGV1j1yphCfeumxJq6yGDS8ZT2RMe+U+N5f6uRpWzLkgLcyG5ZCpLQfZe7Gz0aIU9gHInoUg1fBykyn/6juE0cqQ5LkAWw5/Bl0iwhmTdl9mSPiaRVpupVnhcLvafxXdOlsJKtetFZH3g5c4xLSjcFs0BbXRugGbyA3gr7SCLi4YM6mkoU8jXe7BONcYKntRow/q73BUJULpz8mFWKF6macwTvaRyNJMk/zpyUabBbLJQh7x1/pkRD21Y5k6fylgggaiua9C+i/xqf2MXMJ0BHjNiWAm7/3fiCR2JcsJTKEDxqa/2c5RfKN3LgDTAffz2lOTpiLrAbKF0=",
                "JgAcAQABvID0dnQ3bQ5IbWN6pwK1S2MjMxZjc3a+ejXlJ5ktVDsCr7Guh8DEZNsG4nAjmD5+OEfk94ZY3XHAWdQwnIx5gYhkbfx1bYvX32xaIK2CVDfak7JUwozZ016GgWiHwNmzqU/OAm3jd+cuUARUOxjen+Gwu8YzPsshJp0iiIaRFiLUibyA2BOIB6U8KsdzU583kDl/gXZIWi5DMYtaQGia1tUVJEcRLwB4bnG8pByV0VuazYtUCSF5GyFwVDLwcjWRQrCPbz3vQ/wrmS5uEcdWFIRybx+VcSTrbK96lOn16BC/CcDhLAFnnkj1AWoZ3KgeRrZpNfmW/vt7Yk+54XgLl/HEaoE=",
                "JgAcAQABvID4hEIc6GTeak4mlD4inV6HXkecoipKgPCN3XTB/YgA6f+q2MBryJFEjnW3BEH3TAzfIfS95Hw62l1x6LpC2dCPsmDplDFdwomrgX/kRzJEHDbFlVO8YtGPamjV8CEyOJGL/HGKicKHmnfQ5xun6+U8LJhxMVRGrMYW2e43a5Yf/7yA74ippGC8UkzbOwOIbibQ6p4Whs4J7YTEcv8+5RhZevKox76xlZn2fjHgyavnzAEfRMc6h4fp/BcfvchasCcb60Twk7LLqtvpgg8npBjqIo88xzxjwgtRSs6VyI8Y6wKa/dIIQhazr9M0ftBw7tsVfkh72CXqkmFtVAuAKxhN3sM=",
                "JgAcAQABvIDi1pLkt7liAB0Dd6CCnemJj5sXWUowW+3Q5rSdxSUnfRRTksJpTwJQFLAkBORhS3nSsBbwxvllEXuyEGUu/NhnbFgAaMkfZbJplrmobI/jLGmoYUy+DmzAUCGiHcl382pcnlV9z+VvLEogl9jDr8a+F7gmWU1RXbnVecRNWTzxw7yA1s/tlJvxNnhuRHTme6R7NWridsg9D3q2pBOFxznwPRbHTC8rQOpxNP/OfnMPq3BbMSRQPgNoRIZBu0JhRHSTWgdzMkBIdzumOZ4VhjU5l6akM6P4eUN4NG6nWuUVC96nPMI6mt7LG3fPqtVIYA296PpKceM6FIIcOHi7QlLeG08=",


                "JgAcAQABxAAB2ZLz9pA2qlUys9oomId1YF8u8n8T98ekEv8gYAyBQfnHnhqc\n" +
                        "iPcTe4AoZb+r4h1sBgwhZ39pXXNOZDBOMd+e2UHIHYAZvi6R7lNnOm0waLCH\n" +
                        "H7rNXJLCzPHpp7vhAhwVao9pu5U3Maw6dwAVvb4XBoQs2YyMjpSApQJOPizG\n" +
                        "qf6l4D5HW1AxLbWhlKvcs+wBapb9H7266Kzvf2mK2HARi7aQHO5fA/+YGXwe\n" +
                        "TLjt+iLB2TSOvl4juz6w7nmV18QF88FP1DkMWVxyHnFDaIB9E2XCe80Qr9dh\n" +
                        "GOfJcWefvJcdsvgtJMeEYm87IGt0yI/MlpyWFzjMj7VzT+NtQUBEN8QAAdqS\n" +
                        "4PEfNqpVMrPaKJiHdWCMAtUXsa1VMki4p0wG02pCp4h+ByPqzZx7BgNZxgYM\n" +
                        "IWd/VF0zQvCZ4r05BJNtYrG9Of9XSzyGBmd9Nyjj0TKLLYnt60QZx3Wpu+E9\n" +
                        "JEHlJGm7lTcxpSASABW9qzIzB/SEIs1roH3kSStMiSIWrGyhlIXcs+wBapY6\n" +
                        "SKNwuujh5Ha/9W/+G3HDzZiv0VNZtjNcYSkFHD6RyHcjjJdbe2xEzjxnIcbN\n" +
                        "7UGLsydve5TJPsKtaDxQAjJk0JpVMzruxdhiOb+Otq9IvZtV0weA4cMUNr4N\n" +
                        "5GOL6TeprGf8TFwpJ7DpgxpZYoYMO6r62hn8pIU=\n",

                "JgAcAQABxAABzSj/76kVC9Oo6bBBDkEZIO2FPHl+QQOOkyUSW7X+wyWOq6gW\n" +
                        "/McbqGokidqWXYJfwKPauzH1GQ7oDCoOenPJEi4Jm4oAwKgZ53ngsssHynGs\n" +
                        "+2IJ2NYH61jtMUUp7O0A3lWfMgG9M0amTBGcuSKQ1IalP3cIeiMuo/2zeUat\n" +
                        "jm3GSY2o3vxjwwI40mIrzVjzGG5uPSD5socv0yEnI21utLV/opfJgUsqOIH+\n" +
                        "KlOO2NRZ9/BPrdv/wUP24Cs31rZIs8nfUap/JCXkcP/hBdWDxQ0aLaLIn5E6\n" +
                        "OvkKca4vr6/5AOCe1EJfKhx1K35PDFSxVkCumt/Ryc/NdXF9RacTgcQAAf7z\n" +
                        "J5RIYuMi9trkZJNUryi9Mtk4sK64olv5GG5uPSD5socv0yEnI21utLV/omwm\n" +
                        "ZK+IWp4IMM8KSkth1ONlCz4N1FB/IfKUVHmZPWp9Z9lgAXpC2iXkcP8i/WME\n" +
                        "6AO8s2zx7HkboeTMCoiGPKbY4Xily6cbsJUE4t7P3tWoG7sB5DwC6ornlUhY\n" +
                        "ZPkeNZH4ZMQ9p3pZk9ITurJM2flaJkj0y6ilZCPnuZm3L6SCT0vZXF1h9EMO\n" +
                        "IwtxRz7wzFMJJGVNDLjHH03bpQB865O4CkZuxUZl+f57nDgE1+vRPwdory0o\n" +
                        "zvpyQFRM0usYFfvUDfhk3MSti/SsBTg8VpYLF8c=\n",

                "JgAcAQABxAABwOesjSMPkIiKWfcmcKXVtBSjD1ZpBWpfniY3oJbxO8aqHmpr\n" +
                        "HggJ+NPMBLmGzY6jEyYDpzv3jqS69XSTJmES+CGwTayjyllPp0yJvIysEsoY\n" +
                        "BwrXWFHojnSep8QUoDr6d1WfJ6nnsO+AYZ32AVZylUBGHbT7iGDzJ0zpuBOe\n" +
                        "/ZlmGOFVuuVzpvTbbJ/0iXm/uU0QPF/bz9rl6m86qJ4o7R9qZGb2s1wp6Ft2\n" +
                        "DGjhcD6ie4dhxOpVrO64zn7asMFCwt251CReK9YETWO+FMWgraBP8TnECSX6\n" +
                        "18N6bP/dISRIVfEnflxFJgeOFf0phTcJqR1l/7pAYscuhXcHoQbLt8QAAdee\n" +
                        "gnl3AvS5gRLooTef1tzoZkid4SVhSO6Vyua2i5go97k53D6ie4dhxOpzwpde\n" +
                        "e4yf/I/BQsLdudQkXisFTyTwCFqzQL916WYlFiAxY9UPt3iYTLmSSFYDkEry\n" +
                        "Pb4VYtiN43vP7M28ynzp+CZ35cbIAjsUurvRSTOzbAcWVozRhDl/9s44hF/p\n" +
                        "/gVy5fY9oTzh4sQnHIUbE6nE42B3d34IQdE3MeqhQBttn1OHTQxW+DASDt9d\n" +
                        "pR+5tdhep+rusR57XSsIGRQKefcmrYTZQSz3KdYidUN3yflgGtuVLmu91iIX\n" +
                        "IginhKtFCDcFQg9Bp83ZvSl0RAmBGn7qKAjlfR8=\n",

                "JgAcAQABxAAB6X7cH9NdSxJ1rR/7QeRmDCWM0qNIJkQnI/T8kIAFt2VElm+7\n" +
                        "XeOEpN7tJC85dddWN6hegqW5FrJ8Ug2w8wBuseb/nZpEPeXzKjnAGpd7vrx3\n" +
                        "qfrvQirjCKyVE6OyseLGG1RXvcMTseqdCLAJz/a00SdgqRjK5zH6BhCJiRzV\n" +
                        "8tBsycopGrtPDbHbiSpgmYqvk63nLAxUrD6K/ZdfIN2P7HYekN9Um16L8e9U\n" +
                        "Ro8oqxTAv5kVLr04pA0GajBXl6jUa5Kp/xawSJmOeWY7Hpoi3u2zUa/sMs7O\n" +
                        "RivG9Hbvmj/S89wCjyFd0etLsdT1DH5bnZqWY34pFNuSqOvUKF8AdcQAAfjR\n" +
                        "0ILGcT0oRWN+oa5veOJy0icrk+KpCtDOcDSBLB6glU2HuS75WDJhlWDKcjBC\n" +
                        "m+JdpDRvc+6ISiDs3uUwoMz49mOkGriGJgMwUAnn+o2k+4aL6f2xfOLpGOio\n" +
                        "kKwGXg86zQLFD20qqToxfrZFvzjmVtM9msuNxeJjJtt/2tx6iMogaql8B6Cq\n" +
                        "JLTYuKdb+aJPp8oGNit2ofsp7nbzSKSAXWAX3d25H8HAhJ+xDCJ3r0gmRCcj\n" +
                        "9PydN2XsqHGWb7G9Rs4H0HgXndP9/fHjyiPLa/15BuiluRay4VJnmhFR0Tjr\n" +
                        "EL+nURLBubWit2VY/I0GxfDMdlwz3qi00lLW7ss="
        };

        public static PublicKey publicKey(int index) throws EncryptionError {
            return privateKey(index).getPublicKey();
        }
    }


    static Contract createSimpleContract(PrivateKey key) {
        Contract result = new Contract();
        // default expiration date
        result.setExpiresAt(ZonedDateTime.now().plusDays(90));
        // issuer role is a key for a new contract
        result.setIssuerKeys(key.getPublicKey().getShortAddress());
        // issuer is owner, link roles
        result.registerRole(new RoleLink("owner", "issuer"));
        result.registerRole(new RoleLink("creator", "issuer"));
        return result;
    }

    static Contract createComplexConctract(PrivateKey key, int subcontracts, int n) {
        Contract root = createSimpleContract(key);
        for(int i = 0; i < subcontracts; i++) {
            Contract c = createSimpleContract(key);
            c.setExpiresAt(c.getExpiresAt().plusSeconds(i+1+(subcontracts+1)*n));
            c.seal();
            root.addNewItems(c);
        }
        root.setExpiresAt(root.getExpiresAt().plusSeconds((subcontracts+1)*n));
        root.addSignerKey(key);
        root.seal();

        return root;
    }

    public static void main(String... args) throws Exception {

        if(args.length == 1 && args[0].equals("--info")) {
            System.out.println("AVG0 represents pure client TPS. Measurement starts at the moment\n" +
                    "first registration request is INITIATED. It doesn't take into account\n" +
                    "network latency and contracts upload time. AVG0 is always the lowest.\n");

            System.out.println( "AVG1 represents server TPS. Measurement starts at the moment\n" +
                    "FIRST registration request is COMPLETE - node got contracts\n" +
                    "and started registration. It takes into account network latency and\n" +
                    "measures pure nodes working time.\n");
            System.out.println( "AVG2 is very similar to AVG1. The difference is that measurement starts\n" +
                    "at the moment LAST registration request is COMPLETE - ALL nodes got contracts\n" +
                    "and started registration. A good test run is represented by AVG1 and AVG2 being\n" +
                    "very close to each other as requests are performed to different nodes in parallel\n" +
                    "and must be completed nearly simultaneously.");

            System.out.println( "PEAK represents maximum value of AVG1 calculated every time a single contract is getting\n" +
                    "APPROVED status.\n");
            return;
        } else if(args.length !=2) {
            System.out.println("expected format: java -jar uniperformance.jar TOPOLOGY PATH_TO_ADMIN_KEY");
        }

        final int BUNCHES_PER_NODE = 1;
        final int TIME_BETWEEN_BUNCHES = 3000;


        final int PACK_SIZE = 500;
        final int PACKS_IN_BUNCH = 5;

        System.out.println("Connecting to nodes...");
        Client client = new Client(args[0],null, new PrivateKey(Do.read(args[1])));

        System.out.println("Network version: " + client.getVersion());
        final int NODES_COUNT = client.size();

        final int PACKES_COUNT = BUNCHES_PER_NODE*NODES_COUNT*PACKS_IN_BUNCH;

        HashMap<Integer,Client> clients = new HashMap<>();
        HashMap<Pair<Integer,Integer>, ArrayList<byte[]>> bytes = new HashMap<>();
        HashMap<Pair<Integer,Integer>,ArrayList<Contract>> contracts = new HashMap<>();

        for (int i = 0; i < NODES_COUNT; i++) {
            clients.put(i,client.getClient(i));
            clients.get(i).ping();
        }

        System.out.println("Preparig contracts...");



        ExecutorService executorService = new ScheduledThreadPoolExecutor(NODES_COUNT);

        int l = 0;
        for (int k = 0; k < BUNCHES_PER_NODE; k++) {
            for (int i = 0; i < NODES_COUNT; i++) {
                contracts.put(Pair.of(k,i),new ArrayList<>());
                bytes.put(Pair.of(k,i),new ArrayList<>());

                for (int j = 0; j < PACKS_IN_BUNCH; j++) {
                    l++;
                    final int fk = k;
                    final int fi = i;
                    final int fl = l;
                    executorService.submit(() -> {
                        try {
                            Contract c = createComplexConctract(TestKeys.privateKey(1),PACK_SIZE,fl);
                            synchronized (contracts) {
                                contracts.get(Pair.of(fk, fi)).add(c);
                                bytes.get(Pair.of(fk, fi)).add(c.getPackedTransaction());
                            }
                        } catch (EncryptionError encryptionError) {
                            encryptionError.printStackTrace();
                        }
                    });
                }
            }
        }

        AtomicLong start0 = new AtomicLong(System.currentTimeMillis());
        AtomicLong start1 = new AtomicLong(System.currentTimeMillis());
        AtomicLong start2 = new AtomicLong(System.currentTimeMillis());
        AtomicLong end = new AtomicLong();

        System.out.println("Registering contracts...");
        AtomicInteger sentCounter = new AtomicInteger(0);
        AtomicInteger doneCounter = new AtomicInteger(0);
        AsyncEvent eventReady = new AsyncEvent();
        AtomicInteger peakFps = new AtomicInteger(0);

        for(int i =0; i < NODES_COUNT;i++) {
            int fI = i;
            executorService.submit(() -> {
                for(int k = 0; k < BUNCHES_PER_NODE; k++) {
                    long from = System.currentTimeMillis();
                    try {
                        clients.get(fI).command("startApproval", "packedItems", bytes.get(Pair.of(k,fI)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Sent " + k + " " + fI);
                    long to = System.currentTimeMillis();
                    if(BUNCHES_PER_NODE == 1) {
                        if (to - from < TIME_BETWEEN_BUNCHES) {
                            try {
                                Thread.sleep(TIME_BETWEEN_BUNCHES - (to - from));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                int currentSent = sentCounter.incrementAndGet();
                if(BUNCHES_PER_NODE == 1) {

                    if(currentSent == 1) {
                        start1.set(System.currentTimeMillis());
                    }
                    if(currentSent == NODES_COUNT) {
                        start2.set(System.currentTimeMillis());
                    }
                }



                boolean found;
                for(int k = 0; k < BUNCHES_PER_NODE; k++) {
                    do {
                        found = false;
                        for (int j = 0; j < contracts.get(Pair.of(k,fI)).size(); j++) {
                            try {
                                ItemResult rr;
                                Contract c = contracts.get(Pair.of(k,fI)).get(j);
                                rr = clients.get(fI).getState(c.getId());
                                if (!rr.state.isApproved()) {
                                    found = true;
                                    if (rr.state == ItemState.DECLINED) {
                                        System.out.println(c.getId() + " is DECLINED " + c.getErrors());
                                        System.exit(1);
                                    }
                                } else {
                                    contracts.get(Pair.of(k,fI)).remove(j);
                                    j--;
                                    int done = doneCounter.incrementAndGet();
                                    System.out.print("\rDone:" + done);
                                    if(done == BUNCHES_PER_NODE*PACKS_IN_BUNCH*NODES_COUNT) {
                                        end.set(System.currentTimeMillis());
                                        eventReady.fire();
                                    }

                                    if(BUNCHES_PER_NODE == 1) {
                                        final int newPeak = (int) (PACK_SIZE * done / ((System.currentTimeMillis() - start1.get()) / 1000f));
                                        peakFps.getAndUpdate(curPeak -> newPeak > curPeak ? newPeak : curPeak);
                                    }

                                }

                            } catch (ClientError clientError) {
                                clientError.printStackTrace();
                            }
                        }
                        if (found) {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } while (found);
                }
            });
        }

        eventReady.await();

        float tps0 = PACK_SIZE*PACKES_COUNT / ( (end.get()-start0.get())/1000.0f);
        float tps1 = PACK_SIZE*PACKES_COUNT / ( (end.get()-start1.get())/1000.0f);
        float tps2 = PACK_SIZE*PACKES_COUNT / ( (end.get()-start2.get())/1000.0f);
        System.out.println("\nTPS\n");
        System.out.println("AVG0: "+tps0+"\n");
        if(BUNCHES_PER_NODE == 1) {
            System.out.println("AVG1: "+tps1+"\n");
            System.out.println("AVG2: "+tps2+"\n");
            System.out.println("PEAK: "+peakFps+"\n");
        }
        System.out.println("Run with --info to get values explained\n");


        executorService.shutdownNow();
        System.exit(0);
    }
}
